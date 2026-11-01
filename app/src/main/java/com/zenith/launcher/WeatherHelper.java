package com.zenith.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class WeatherHelper {

    private static final String TAG = "ZenithWeather";
    private static final String PREF_NAME = "zenith_weather_cache";
    private static final String KEY_CACHED_WEATHER = "cached_weather_str";
    private static final String KEY_CACHED_CITY = "cached_city";
    private static final String KEY_CACHED_LAT = "cached_lat";
    private static final String KEY_CACHED_LON = "cached_lon";

    public interface WeatherCallback {
        void onWeatherLoaded(String weatherText);
        void onError();
    }

    public static String getCachedWeather(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_CACHED_WEATHER, "⛅ Meteo in caricamento...");
    }

    public static void fetchWeather(final Context context, final WeatherCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    String city = sp.getString(KEY_CACHED_CITY, null);
                    double lat = Double.longBitsToDouble(sp.getLong(KEY_CACHED_LAT, Double.doubleToLongBits(0.0)));
                    double lon = Double.longBitsToDouble(sp.getLong(KEY_CACHED_LON, Double.doubleToLongBits(0.0)));

                    // 1. If location is not cached, detect location automatically from IP
                    if (city == null || (lat == 0.0 && lon == 0.0)) {
                        GeoLocation geo = detectLocationFromIp();
                        if (geo != null) {
                            city = geo.city;
                            lat = geo.lat;
                            lon = geo.lon;

                            sp.edit()
                                    .putString(KEY_CACHED_CITY, city)
                                    .putLong(KEY_CACHED_LAT, Double.doubleToLongBits(lat))
                                    .putLong(KEY_CACHED_LON, Double.doubleToLongBits(lon))
                                    .apply();
                        }
                    }

                    // Fallback default coordinates (Rome) if IP geolocation failed and no cache
                    if (city == null || (lat == 0.0 && lon == 0.0)) {
                        lat = 41.8933;
                        lon = 12.4829;
                        city = "Meteo";
                    }

                    // 2. Fetch current weather from Open-Meteo
                    String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude="
                            + lat + "&longitude=" + lon + "&current_weather=true";
                    Log.d(TAG, "Fetching weather for " + city + " from: " + weatherUrl);
                    String weatherJson = httpGet(weatherUrl, 10000);

                    if (weatherJson != null && !weatherJson.isEmpty()) {
                        JSONObject root = new JSONObject(weatherJson);
                        JSONObject current = root.getJSONObject("current_weather");
                        double temp = current.getDouble("temperature");
                        int code = current.getInt("weathercode");

                        String condition = interpretWeatherCode(code);
                        final String result = condition + " " + Math.round(temp) + "°C • " + city;

                        // Save to cache
                        sp.edit().putString(KEY_CACHED_WEATHER, result).apply();

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onWeatherLoaded(result);
                            }
                        });
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Weather fetch exception", e);
                }

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError();
                    }
                });
            }
        }).start();
    }

    private static class GeoLocation {
        String city;
        double lat;
        double lon;
        GeoLocation(String city, double lat, double lon) {
            this.city = city;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static GeoLocation detectLocationFromIp() {
        // Attempt 1: ipwho.is (HTTPS, free, high accuracy)
        try {
            String json = httpGet("https://ipwho.is/?fields=success,city,latitude,longitude", 6000);
            if (json != null && !json.isEmpty()) {
                JSONObject obj = new JSONObject(json);
                if (obj.optBoolean("success", false)) {
                    String city = obj.optString("city", "");
                    double lat = obj.optDouble("latitude", 0.0);
                    double lon = obj.optDouble("longitude", 0.0);
                    if (!city.isEmpty() && lat != 0.0) {
                        return new GeoLocation(city, lat, lon);
                    }
                }
            }
        } catch (Exception ignored) {}

        // Attempt 2: ip-api.com (HTTP fallback)
        try {
            String json = httpGet("http://ip-api.com/json/?fields=status,city,lat,lon", 6000);
            if (json != null && !json.isEmpty()) {
                JSONObject obj = new JSONObject(json);
                if ("success".equalsIgnoreCase(obj.optString("status"))) {
                    String city = obj.optString("city", "");
                    double lat = obj.optDouble("lat", 0.0);
                    double lon = obj.optDouble("lon", 0.0);
                    if (!city.isEmpty() && lat != 0.0) {
                        return new GeoLocation(city, lat, lon);
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String interpretWeatherCode(int code) {
        if (code == 0) return "☀️ Sereno";
        if (code >= 1 && code <= 3) return "⛅ Poco nuvoloso";
        if (code == 45 || code == 48) return "🌫️ Nebbia";
        if (code >= 51 && code <= 67) return "🌧️ Pioggia";
        if (code >= 71 && code <= 77) return "❄️ Neve";
        if (code >= 80 && code <= 82) return "🌦️ Rovesci";
        if (code >= 95) return "⛈️ Temporale";
        return "🌤️ Nuvoloso";
    }

    private static String httpGet(String urlStr, int timeout) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestProperty("User-Agent", "ZenithLauncher/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "httpGet failed for " + urlStr, e);
        }
        return null;
    }
}
