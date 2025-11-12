package com.farmmonitor.agriai;

import com.google.gson.annotations.SerializedName;

public class WeatherResponse {
    @SerializedName("main")
    public Main main;

    @SerializedName("wind")
    public Wind wind;

    @SerializedName("weather")
    public Weather[] weather;

    public static class Main {
        @SerializedName("temp")
        public Double temp;

        @SerializedName("humidity")
        public Integer humidity;

        @SerializedName("feels_like")
        public Double feels_like;

        @SerializedName("temp_min")
        public Double temp_min;

        @SerializedName("temp_max")
        public Double temp_max;

        @SerializedName("pressure")
        public Integer pressure;
    }

    public static class Wind {
        @SerializedName("speed")
        public Double speed;

        @SerializedName("deg")
        public Integer deg;
    }

    public static class Weather {
        @SerializedName("id")
        public Integer id;

        @SerializedName("main")
        public String main;

        @SerializedName("description")
        public String description;

        @SerializedName("icon")
        public String icon;
    }
}