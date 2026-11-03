package com.zenith.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class WeatherHelper {

    private static final String TAG = "ZenithWeather";
    private static final String PREF_NAME = "zenith_weather_cache";
    private static final String KEY_CACHED_WEATHER = "cached_weather_str";
    private static final String KEY_CACHED_CITY = "cached_city";
    private static final String KEY_CACHED_LAT = "cached_lat";
    private static final String KEY_CACHED_LON = "cached_lon";
    private static final String KEY_MANUAL_CITY = "manual_city_enabled";

    public interface WeatherCallback {
        void onWeatherLoaded(String weatherText);
        void onError();
    }

    public interface GeocodeCallback {
        void onSuccess(String foundCity, double lat, double lon);
        void onError(String message);
    }

    public static String getCachedWeather(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_CACHED_WEATHER, "⛅ Meteo in caricamento...");
    }

    public static String getCurrentCityDisplay(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isManual = sp.getBoolean(KEY_MANUAL_CITY, false);
        String city = sp.getString(KEY_CACHED_CITY, null);
        if (city == null || city.isEmpty()) {
            return "Automatico (IP)";
        }
        return isManual ? city : (city + " (Auto)");
    }

    public static boolean isManualLocation(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_MANUAL_CITY, false);
    }

    public static void setManualCity(Context context, String city, double lat, double lon) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putBoolean(KEY_MANUAL_CITY, true)
                .putString(KEY_CACHED_CITY, city)
                .putLong(KEY_CACHED_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_CACHED_LON, Double.doubleToLongBits(lon))
                .apply();
    }

    public static void resetToAutoLocation(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putBoolean(KEY_MANUAL_CITY, false)
                .remove(KEY_CACHED_CITY)
                .remove(KEY_CACHED_LAT)
                .remove(KEY_CACHED_LON)
                .remove(KEY_CACHED_WEATHER)
                .apply();
    }

    public static void searchAndSetCity(final Context context, final String query, final GeocodeCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String encoded = URLEncoder.encode(query.trim(), "UTF-8");
                    String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                            + encoded + "&count=5&language=it&format=json";
                    String json = httpGet(url, 8000);
                    if (json != null && !json.isEmpty()) {
                        JSONObject root = new JSONObject(json);
                        if (root.has("results")) {
                            JSONArray results = root.getJSONArray("results");
                            if (results.length() > 0) {
                                JSONObject best = null;
                                for (int i = 0; i < results.length(); i++) {
                                    JSONObject item = results.getJSONObject(i);
                                    if ("IT".equalsIgnoreCase(item.optString("country_code"))) {
                                        best = item;
                                        break;
                                    }
                                }
                                if (best == null) {
                                    best = results.getJSONObject(0);
                                }

                                final String foundCity = best.getString("name");
                                final double lat = best.getDouble("latitude");
                                final double lon = best.getDouble("longitude");

                                setManualCity(context, foundCity, lat, lon);

                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (callback != null) {
                                            callback.onSuccess(foundCity, lat, lon);
                                        }
                                    }
                                });
                                return;
                            }
                        }
                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onError("Nessuna località trovata per '" + query + "'");
                            }
                        }
                    });
                } catch (final Exception e) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onError("Errore ricerca: " + e.getMessage());
                            }
                        }
                    });
                }
            }
        }).start();
    }

    public static void fetchWeather(final Context context, final WeatherCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    boolean isManual = sp.getBoolean(KEY_MANUAL_CITY, false);
                    String city = sp.getString(KEY_CACHED_CITY, null);
                    double lat = Double.longBitsToDouble(sp.getLong(KEY_CACHED_LAT, Double.doubleToLongBits(0.0)));
                    double lon = Double.longBitsToDouble(sp.getLong(KEY_CACHED_LON, Double.doubleToLongBits(0.0)));

                    // 1. If not manual and coordinates are missing, detect from IP
                    if (!isManual && (city == null || (lat == 0.0 && lon == 0.0))) {
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
                        city = "Roma";
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

                        String icon = getWeatherIcon(code);
                        String condition = interpretWeatherCode(code);
                        final String result = icon + " " + city + " • " + Math.round(temp) + "°C " + condition;

                        // Save to cache
                        sp.edit().putString(KEY_CACHED_WEATHER, result).apply();

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onWeatherLoaded(result);
                                }
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
                        if (callback != null) {
                            callback.onError();
                        }
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

    public static String interpretWeatherCode(int code) {
        if (code == 0) return "Sereno";
        if (code == 1) return "Prevalentemente sereno";
        if (code == 2) return "Poco nuvoloso";
        if (code == 3) return "Coperto";
        if (code == 45 || code == 48) return "Nebbia";
        if (code >= 51 && code <= 55) return "Pioviggine";
        if (code >= 56 && code <= 57) return "Pioviggine gelata";
        if (code >= 61 && code <= 65) return "Pioggia";
        if (code >= 66 && code <= 67) return "Pioggia gelata";
        if (code >= 71 && code <= 77) return "Neve";
        if (code >= 80 && code <= 82) return "Rovesci";
        if (code >= 85 && code <= 86) return "Rovesci di neve";
        if (code >= 95) return "Temporale";
        return "Nuvoloso";
    }

    public static String getWeatherIcon(int code) {
        if (code == 0) return "☀️";
        if (code == 1 || code == 2) return "⛅";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77) return "❄️";
        if (code >= 80 && code <= 82) return "🌦️";
        if (code >= 85 && code <= 86) return "🌨️";
        if (code >= 95) return "⛈️";
        return "🌤️";
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
