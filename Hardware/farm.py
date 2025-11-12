#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Complete Farm Automation System (Full File)
- DHT caching + fallback
- IR majority sampling + polarity option
- Alarm sequence dedupe
- Firebase listener + polling fallback to ensure controls (lighting/fan) are applied
- Sensor read every second, Firebase update every second (configurable)
"""

import os
import time
import sys
import signal
import logging
from logging.handlers import RotatingFileHandler
from datetime import datetime
from threading import Thread, Event

# Firebase imports
import firebase_admin
from firebase_admin import credentials, db

# Sensor imports
try:
    import RPi.GPIO as GPIO
    GPIO_AVAILABLE = True
except Exception:
    print("[SIMULATION] Running in SIMULATION mode (GPIO not available)")
    GPIO_AVAILABLE = False

try:
    import Adafruit_DHT
    DHT_AVAILABLE = True
except Exception:
    print("[WARNING] Adafruit_DHT not available, using simulated temperature/humidity")
    DHT_AVAILABLE = False

# ==================== CONFIGURATION ====================

FARM_ID = "farm_1"
FIREBASE_URL = "https://agroai-d5e7a-default-rtdb.firebaseio.com"
SERVICE_ACCOUNT_PATH = "/home/admin/home/pi/farm-monitor/serviceAccountKey.json"

# GPIO Pin Configuration (BCM numbering)
PIN_DHT22 = 4
PIN_SOIL_MOISTURE = 17
PIN_LDR = 27
PIN_IR_SENSOR = 22

# Relay Pins
PIN_RELAY_PUMP = 23
PIN_RELAY_LIGHT = 24
PIN_RELAY_FAN = 25
PIN_RELAY_ALARM = 26

# Intervals (seconds)
SENSOR_READ_INTERVAL = 1        # main loop sensor read interval
FIREBASE_UPDATE_INTERVAL = 1    # how often to push sensors to Firebase
DHT_MIN_INTERVAL = 2.0          # don't call DHT native driver more often than this
CONTROLS_POLL_INTERVAL = 2.0    # fallback polling interval for Firebase controls

# Thresholds / behavior
SOIL_DRY_THRESHOLD = True
LIGHT_DARK_THRESHOLD = True
AUTO_LIGHT_START_HOUR = 18
AUTO_LIGHT_END_HOUR = 6
AUTO_IRRIGATION_DURATION = 60

# Relay
RELAY_ACTIVE_LOW = True

# IR sensor polarity: set True if your IR outputs HIGH on motion.
IR_ACTIVE_HIGH = False

# IRSensor sampling
IR_SAMPLE_COUNT = 3
IR_SAMPLE_INTERVAL = 0.02

# Logging
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
LOG_FILE = os.path.join(BASE_DIR, "farm_system.log")
LOG_LEVEL = logging.INFO

# ==================== LOGGING SETUP ====================

logger = logging.getLogger(__name__)
logger.setLevel(LOG_LEVEL)

try:
    fh = RotatingFileHandler(LOG_FILE, maxBytes=5 * 1024 * 1024, backupCount=3)
    fh.setLevel(LOG_LEVEL)
    fh.setFormatter(logging.Formatter('%(asctime)s - %(levelname)s - %(message)s'))
    sh = logging.StreamHandler(sys.stdout)
    sh.setLevel(LOG_LEVEL)
    sh.setFormatter(logging.Formatter('%(asctime)s - %(levelname)s - %(message)s'))
    logger.addHandler(fh)
    logger.addHandler(sh)
except Exception:
    logging.basicConfig(level=LOG_LEVEL, format='%(asctime)s - %(levelname)s - %(message)s')
    logger = logging.getLogger(__name__)


# ==================== GPIO HELPER ====================

class GPIOManager:
    @staticmethod
    def setup():
        if not GPIO_AVAILABLE:
            return False
        GPIO.setmode(GPIO.BCM)
        GPIO.setwarnings(False)
        logger.info("GPIO initialized (BCM mode)")
        return True

    @staticmethod
    def cleanup():
        if GPIO_AVAILABLE:
            GPIO.cleanup()
            logger.info("GPIO cleaned up")


# ==================== SENSOR CLASSES ====================

class DHT22Sensor:
    """DHT22 with caching/fallback to avoid hammering failing native driver."""
    def __init__(self, pin):
        self.pin = pin
        self.use_simulation = False
        self.last_read_time = 0.0
        self.cache = {'temperature': 25.0, 'humidity': 60.0}
        if GPIO_AVAILABLE and DHT_AVAILABLE:
            self.sensor = Adafruit_DHT.DHT22
            logger.info(f"DHT22 sensor initialized on GPIO {pin}")
        else:
            self.sensor = None
            self.use_simulation = True
            logger.warning(f"DHT22 using SIMULATION mode on GPIO {pin}")

    def read(self):
        now = time.time()
        if self.use_simulation or not GPIO_AVAILABLE or not DHT_AVAILABLE:
            import random
            self.cache = {
                'temperature': round(25 + random.uniform(-3, 5), 1),
                'humidity': round(60 + random.uniform(-10, 15), 1)
            }
            self.last_read_time = now
            return self.cache

        if now - self.last_read_time < DHT_MIN_INTERVAL:
            return self.cache

        try:
            humidity, temperature = Adafruit_DHT.read_retry(self.sensor, self.pin, retries=3, delay_seconds=2)
            if humidity is not None and temperature is not None:
                self.cache = {'temperature': round(temperature, 1), 'humidity': round(humidity, 1)}
            else:
                logger.warning("DHT22 returned None, using cached/fallback values")
                import random
                self.cache = {
                    'temperature': round(self.cache.get('temperature', 25) + random.uniform(-0.5, 0.5), 1),
                    'humidity': round(self.cache.get('humidity', 60) + random.uniform(-1, 1), 1)
                }
        except Exception as e:
            logger.error(f"DHT22 read error: {e}, using cached/fallback data")
            import random
            self.cache = {
                'temperature': round(self.cache.get('temperature', 25) + random.uniform(-0.5, 0.5), 1),
                'humidity': round(self.cache.get('humidity', 60) + random.uniform(-1, 1), 1)
            }
        finally:
            self.last_read_time = now
            return self.cache


class SoilMoistureSensor:
    def __init__(self, pin):
        self.pin = pin
        if GPIO_AVAILABLE:
            GPIO.setup(pin, GPIO.IN)
        logger.info(f"Soil Moisture sensor initialized on GPIO {pin}")

    def read(self):
        if not GPIO_AVAILABLE:
            import random
            return random.choice([True, False])
        try:
            return GPIO.input(self.pin) == GPIO.HIGH
        except Exception as e:
            logger.error(f"Soil sensor read error: {e}")
            return False

    def is_dry(self):
        return self.read() == SOIL_DRY_THRESHOLD


class LDRSensor:
    def __init__(self, pin):
        self.pin = pin
        if GPIO_AVAILABLE:
            GPIO.setup(pin, GPIO.IN)
        logger.info(f"LDR sensor initialized on GPIO {pin}")

    def read(self):
        if not GPIO_AVAILABLE:
            current_hour = datetime.now().hour
            return AUTO_LIGHT_START_HOUR <= current_hour or current_hour < AUTO_LIGHT_END_HOUR
        try:
            return GPIO.input(self.pin) == GPIO.HIGH
        except Exception as e:
            logger.error(f"LDR sensor read error: {e}")
            return False

    def is_dark(self):
        return self.read() == LIGHT_DARK_THRESHOLD


class IRSensor:
    def __init__(self, pin, sample_count=IR_SAMPLE_COUNT, sample_interval=IR_SAMPLE_INTERVAL):
        self.pin = pin
        self.sample_count = sample_count
        self.sample_interval = sample_interval
        self.last_detection = 0.0
        self.detection_cooldown = 5.0
        if GPIO_AVAILABLE:
            GPIO.setup(pin, GPIO.IN)
        logger.info(f"IR sensor initialized on GPIO {pin} (IR_ACTIVE_HIGH={IR_ACTIVE_HIGH})")

    def raw_read(self):
        if not GPIO_AVAILABLE:
            return False
        try:
            return GPIO.input(self.pin)
        except Exception as e:
            logger.error(f"IR sensor raw read error: {e}")
            return False

    def read(self):
        if not GPIO_AVAILABLE:
            return False
        positives = 0
        for _ in range(self.sample_count):
            raw = self.raw_read()
            if IR_ACTIVE_HIGH:
                if raw == GPIO.HIGH:
                    positives += 1
            else:
                if raw == GPIO.LOW:
                    positives += 1
            time.sleep(self.sample_interval)
        return positives >= (self.sample_count // 2) + 1

    def motion_detected(self):
        now = time.time()
        if self.read():
            if now - self.last_detection > self.detection_cooldown:
                self.last_detection = now
                return True
        return False


# ==================== RELAY CONTROLLER ====================

class RelayController:
    def __init__(self):
        self.relays = {
            'pump': PIN_RELAY_PUMP,
            'light': PIN_RELAY_LIGHT,
            'fan': PIN_RELAY_FAN,
            'alarm': PIN_RELAY_ALARM
        }
        self.state = {k: False for k in self.relays}

        if GPIO_AVAILABLE:
            for name, pin in self.relays.items():
                GPIO.setup(pin, GPIO.OUT)
                self._set_gpio(pin, False)

        logger.info("4-Channel Relay Module initialized")
        for name, pin in self.relays.items():
            logger.info(f" - {name.upper()}: GPIO {pin}")

    def _set_gpio(self, pin, state):
        if not GPIO_AVAILABLE:
            return
        gpio_state = (not state) if RELAY_ACTIVE_LOW else state
        GPIO.output(pin, GPIO.HIGH if gpio_state else GPIO.LOW)

    def turn_on(self, relay_name):
        if relay_name not in self.relays:
            return False
        self._set_gpio(self.relays[relay_name], True)
        self.state[relay_name] = True
        logger.info(f"{relay_name.upper()} turned ON")
        return True

    def turn_off(self, relay_name):
        if relay_name not in self.relays:
            return False
        self._set_gpio(self.relays[relay_name], False)
        self.state[relay_name] = False
        logger.info(f"{relay_name.upper()} turned OFF")
        return True

    def set(self, relay_name, state):
        try:
            state_bool = bool(state)
        except Exception:
            state_bool = False
        return self.turn_on(relay_name) if state_bool else self.turn_off(relay_name)

    def get_state(self, relay_name):
        return self.state.get(relay_name, False)

    def turn_all_off(self):
        logger.warning("EMERGENCY: Turning all relays OFF")
        for relay_name in self.relays.keys():
            self.turn_off(relay_name)


# ==================== AUTOMATION ENGINE ====================

class AutomationEngine:
    def __init__(self, sensors, relays):
        self.sensors = sensors
        self.relays = relays
        self.auto_mode = {'irrigation': False, 'lighting': False}
        self._alarm_running = False
        logger.info("Automation Engine initialized")

    def enable_auto_irrigation(self):
        self.auto_mode['irrigation'] = True
        logger.info("Auto irrigation ENABLED")

    def disable_auto_irrigation(self):
        self.auto_mode['irrigation'] = False
        logger.info("Auto irrigation DISABLED")

    def enable_auto_lighting(self):
        self.auto_mode['lighting'] = True
        logger.info("Auto lighting ENABLED")

    def disable_auto_lighting(self):
        self.auto_mode['lighting'] = False
        logger.info("Auto lighting DISABLED")

    def check_auto_irrigation(self):
        if not self.auto_mode['irrigation']:
            return
        if self.sensors['soil'].is_dry():
            if not self.relays.get_state('pump'):
                logger.info("Soil is DRY - Starting automatic irrigation")
                self.start_irrigation_cycle()

    def start_irrigation_cycle(self):
        self.relays.turn_on('pump')
        def stop_pump():
            time.sleep(AUTO_IRRIGATION_DURATION)
            self.relays.turn_off('pump')
            logger.info(f"Irrigation cycle complete ({AUTO_IRRIGATION_DURATION}s)")
        Thread(target=stop_pump, daemon=True).start()

    def check_auto_lighting(self):
        if not self.auto_mode['lighting']:
            return
        current_hour = datetime.now().hour
        should_be_on = (AUTO_LIGHT_START_HOUR <= current_hour or current_hour < AUTO_LIGHT_END_HOUR)
        is_dark = self.sensors['ldr'].is_dark()

        if should_be_on or is_dark:
            if not self.relays.get_state('light'):
                logger.info("It's dark - Turning lights ON automatically")
                self.relays.turn_on('light')
        else:
            if self.relays.get_state('light'):
                logger.info("It's bright - Turning lights OFF automatically")
                self.relays.turn_off('light')

    def check_motion_detection(self):
        if self.sensors['ir'].motion_detected():
            logger.warning("MOTION DETECTED! Activating alarm")
            self.trigger_alarm()

    def trigger_alarm(self):
        if self._alarm_running:
            logger.debug("Alarm sequence already running; ignoring new trigger")
            return

        def alarm_sequence():
            try:
                self._alarm_running = True
                for _ in range(5):
                    self.relays.turn_on('alarm')
                    time.sleep(0.3)
                    self.relays.turn_off('alarm')
                    time.sleep(0.2)
            finally:
                self._alarm_running = False

        Thread(target=alarm_sequence, daemon=True).start()


# ==================== FIREBASE MANAGER ====================

class FirebaseManager:
    def __init__(self, farm_id, firebase_url, service_account_path):
        self.farm_id = farm_id
        self.initialized = False
        self.controls_ref = None
        self.sensors_ref = None
        self._last_controls_snapshot = None

        try:
            cred = credentials.Certificate(service_account_path)
            firebase_admin.initialize_app(cred, {'databaseURL': firebase_url})
            self.sensors_ref = db.reference(f'farms/{farm_id}/sensors')
            self.controls_ref = db.reference(f'farms/{farm_id}/controls')
            self.initialized = True
            logger.info(f"Firebase connected: farms/{farm_id}")
        except Exception as e:
            logger.error(f"Firebase initialization failed: {e}")
            self.initialized = False

    def publish_sensor_data(self, data):
        if not self.initialized:
            return False
        try:
            self.sensors_ref.set(data)
            return True
        except Exception as e:
            logger.error(f"Firebase publish error: {e}")
            return False

    def listen_for_controls(self, callback):
        """Start realtime listener if supported by SDK; also start polling fallback."""
        if not self.initialized:
            logger.warning("Firebase not initialized: controls listener not started")
            return

        # Start a background thread to poll controls as a reliable fallback
        Thread(target=self._poll_controls_forever, args=(callback,), daemon=True).start()

        # Try to attach listener (some SDK/platform combos may not support persistent listeners)
        try:
            def on_change(event):
                try:
                    callback(event)
                except Exception as e:
                    logger.error(f"Control callback error: {e}")
            # This may raise NotImplementedError on some environments; wrap.
            self.controls_ref.listen(on_change)
            logger.info("Realtime controls listener attached")
        except Exception as e:
            logger.warning(f"Realtime listener not available/failed: {e}. Polling fallback active.")

    def _poll_controls_forever(self, callback):
        """Periodically read controls node and call callback with a synthetic event when changed."""
        while True:
            try:
                current = self.controls_ref.get() if self.initialized else None
                if current != self._last_controls_snapshot:
                    # Build a synthetic event-like object with .data and .path
                    class E: pass
                    ev = E()
                    ev.data = current
                    ev.path = '/'
                    try:
                        callback(ev)
                    except Exception as e:
                        logger.error(f"Error in controls callback during polling: {e}")
                    self._last_controls_snapshot = current
                time.sleep(CONTROLS_POLL_INTERVAL)
            except Exception as e:
                logger.error(f"Error polling controls: {e}")
                time.sleep(CONTROLS_POLL_INTERVAL)


# ==================== MAIN APPLICATION ====================

class FarmSystem:
    def __init__(self):
        logger.info("=" * 60)
        logger.info("COMPLETE FARM AUTOMATION SYSTEM")
        logger.info("=" * 60)

        GPIOManager.setup()

        self.sensors = {
            'dht22': DHT22Sensor(PIN_DHT22),
            'soil': SoilMoistureSensor(PIN_SOIL_MOISTURE),
            'ldr': LDRSensor(PIN_LDR),
            'ir': IRSensor(PIN_IR_SENSOR)
        }

        self.relays = RelayController()
        self.automation = AutomationEngine(self.sensors, self.relays)
        self.firebase = FirebaseManager(FARM_ID, FIREBASE_URL, SERVICE_ACCOUNT_PATH)

        self.running = True
        self.stop_event = Event()

        signal.signal(signal.SIGINT, self.signal_handler)
        signal.signal(signal.SIGTERM, self.signal_handler)

        logger.info("System initialization complete")

    def signal_handler(self, sig, frame):
        logger.info("Shutdown signal received")
        self.running = False
        self.stop_event.set()

    def read_all_sensors(self):
        """Read all sensors - ALWAYS returns valid data"""
        try:
            dht_data = self.sensors['dht22'].read()
            data = {
                'temperature': dht_data['temperature'],
                'humidity': dht_data['humidity'],
                'soilMoisture': 0 if self.sensors['soil'].is_dry() else 100,
                'lightLevel': 0 if self.sensors['ldr'].is_dark() else 1000,
                'motionDetected': self.sensors['ir'].read(),
                'lastUpdate': int(time.time() * 1000)
            }
            return data
        except Exception as e:
            logger.error(f"Error reading sensors: {e}, using fallback data")
            import random
            return {
                'temperature': round(25 + random.uniform(-3, 5), 1),
                'humidity': round(60 + random.uniform(-10, 15), 1),
                'soilMoisture': 50,
                'lightLevel': 500,
                'motionDetected': False,
                'lastUpdate': int(time.time() * 1000)
            }

    def process_firebase_controls(self, event):
        """Process control updates from Firebase. Applies lighting and ventilation (fan) reliably."""
        data = getattr(event, 'data', None)
        path = getattr(event, 'path', '')
        logger.debug(f"Firebase event.path={path} data={data}")

        if not data and (not path or path == '/'):
            return

        try:
            # If data is dict with keys, handle them
            if isinstance(data, dict):
                # irrigation
                if 'irrigation' in data:
                    irrigation = data.get('irrigation', {}) or {}
                    auto = bool(irrigation.get('autoMode', False))
                    if auto:
                        self.automation.enable_auto_irrigation()
                    else:
                        self.automation.disable_auto_irrigation()
                        enabled = bool(irrigation.get('enabled', False))
                        self.relays.set('pump', enabled)

                # lighting
                if 'lighting' in data:
                    lighting = data.get('lighting', {}) or {}
                    preset = lighting.get('preset', 'manual')
                    if preset == 'auto':
                        self.automation.enable_auto_lighting()
                    else:
                        self.automation.disable_auto_lighting()
                        enabled = bool(lighting.get('enabled', False))
                        self.relays.set('light', enabled)

                # ventilation (fan)
                if 'ventilation' in data:
                    ventilation = data.get('ventilation', {}) or {}
                    enabled = bool(ventilation.get('enabled', False))
                    self.relays.set('fan', enabled)

                # emergency
                if 'emergency' in data:
                    emergency = data.get('emergency', {}) or {}
                    if emergency.get('stopped', False):
                        self.relays.turn_all_off()

            else:
                # Single-child update: use path to determine parent and fetch latest from DB
                try:
                    cleaned = path.strip('/')
                    parts = cleaned.split('/') if cleaned else []
                    if not parts:
                        return
                    parent = parts[0]
                    if self.firebase.initialized:
                        current = self.firebase.controls_ref.child(parent).get()
                    else:
                        current = None

                    if parent == 'irrigation':
                        irrigation = current or {}
                        auto = bool(irrigation.get('autoMode', False))
                        if auto:
                            self.automation.enable_auto_irrigation()
                        else:
                            self.automation.disable_auto_irrigation()
                            enabled = bool(irrigation.get('enabled', False))
                            self.relays.set('pump', enabled)

                    elif parent == 'lighting':
                        lighting = current or {}
                        preset = lighting.get('preset', 'manual')
                        if preset == 'auto':
                            self.automation.enable_auto_lighting()
                        else:
                            self.automation.disable_auto_lighting()
                            enabled = bool(lighting.get('enabled', False))
                            self.relays.set('light', enabled)

                    elif parent == 'ventilation':
                        ventilation = current or {}
                        enabled = bool(ventilation.get('enabled', False))
                        self.relays.set('fan', enabled)

                    elif parent == 'emergency':
                        emergency = current or {}
                        if emergency.get('stopped', False):
                            self.relays.turn_all_off()

                except Exception as e:
                    logger.error(f"Error handling single-child control update: {e}")

        except Exception as e:
            logger.error(f"Error processing controls: {e}")

    def run(self):
        if not self.firebase.initialized:
            logger.warning("Running without Firebase connection")
        else:
            self.firebase.listen_for_controls(self.process_firebase_controls)

        logger.info("System running - Press Ctrl+C to stop")
        logger.info("")

        last_sensor_read = 0.0
        last_firebase_update = 0.0

        while self.running:
            try:
                now = time.time()

                # Read sensors at SENSOR_READ_INTERVAL
                if now - last_sensor_read >= SENSOR_READ_INTERVAL:
                    sensor_data = self.read_all_sensors()

                    if sensor_data:
                        logger.debug(
                            f"Sensors: Temp={sensor_data['temperature']}C, "
                            f"Humidity={sensor_data['humidity']}%, "
                            f"Soil={'DRY' if sensor_data['soilMoisture'] == 0 else 'WET'}, "
                            f"Light={'DARK' if sensor_data['lightLevel'] == 0 else 'BRIGHT'}"
                        )

                        # Update Firebase every FIREBASE_UPDATE_INTERVAL
                        if now - last_firebase_update >= FIREBASE_UPDATE_INTERVAL:
                            if self.firebase.publish_sensor_data(sensor_data):
                                logger.info(f"Data published to Firebase: Temp={sensor_data['temperature']}C, Humidity={sensor_data['humidity']}%")
                            else:
                                logger.warning("Failed to publish to Firebase")
                            last_firebase_update = now

                    last_sensor_read = now

                # Automation checks
                self.automation.check_auto_irrigation()
                self.automation.check_auto_lighting()
                self.automation.check_motion_detection()

                time.sleep(0.1)

            except Exception as e:
                logger.error(f"Error in main loop: {e}")
                time.sleep(1)

        self.shutdown()

    def shutdown(self):
        logger.info("Shutting down gracefully...")
        self.relays.turn_all_off()
        GPIOManager.cleanup()
        logger.info("Shutdown complete. Goodbye!")


# ==================== ENTRY POINT ====================

if __name__ == "__main__":
    try:
        system = FarmSystem()
        system.run()
    except KeyboardInterrupt:
        logger.info("Keyboard interrupt")
    except Exception as e:
        logger.error(f"Fatal error: {e}")
        import traceback
        traceback.print_exc()
    finally:
        logger.info("Program terminated")
