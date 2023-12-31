package weatherdata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.simple.parser.*;
import org.apache.tomcat.util.json.ParseException;
import org.json.simple.*;


@SuppressWarnings("serial")
//@WebServlet("/getData")
public class DataServlet extends HttpServlet {
	
    
    private String zip, line, cityName, destination = "data.jsp";
    private String urlString, noaaURL, forecastURL;
    private JSONArray weatherArray, periods;
    private double latitude, longitude;
    private Object object;
    private JSONObject jObject;
    private Date sunrise, sunset;
    SimpleDateFormat df = new SimpleDateFormat("hh:mm:ss a");
    //Maps for parsing JSON files
    private Map<String, Object> coordinates = new HashMap<>();
    private Map<String, Object> main = new HashMap<>();
    private Map<String, Object> jsonMap = new HashMap<>();
    private Map<String, Object> wind = new HashMap<>();
    private Map<String, Object> weather = new HashMap<>();
    private Map<String, Object> noaaProperties = new HashMap<>();
    private Map<String, Object> forecast = new HashMap<>();
    private Map<String, Object> sysMap = new HashMap<>();
    private Map<String, Object> weatherAPIcurrent = new HashMap<>();
    private Map<String, Object> weatherAPIlocation = new HashMap<>();
    
    private URL url;
    private URLConnection connect;
    private StringBuilder newString;
    private BufferedReader reader;
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    	//Map per prendere le chiavi API dalle variabili d'ambiente
    	Map<String, String> env = System.getenv();
    	final String API_KEY = env.get("OWM_KEY"); //OpenWeatherMap API Key
        final String weatherAPI = env.get("WEATHERAPI_KEY"); //weatherAPI.com API Key
        final String googleAPI = env.get("GOOGLE_KEY"); //Google Maps API Key
        final String climaCellAPI = env.get("CLIMACELL_KEY"); //ClimaCell API
        final String mapBoxToken = env.get("MAPBOX_KEY");
        System.out.println("API: " + API_KEY);
        for (String envName : env.keySet()) {
            System.out.format("%s=%s%n",
                              envName,
                              env.get(envName));
        }
    	//prende il codice zip dall' index.jsp
        zip = request.getParameter("zip");
        //Crea il primo URL per openWeatherMap con zip, chiave API e imposta unit� su imperiale
        urlString = "http://api.openweathermap.org/data/2.5/weather?zip=" + zip + ",us&appid=" + API_KEY + "&units=imperial";
        /*
         * getData prende una stringa URL, stabilisce la connessione, recupera i dati JSON e li aggiunge a un oggetto StringBuilder chiamato newString
         * 
         * */
        getData(urlString);
        try {   
        	//Assegna un nuovo JSONParser all'oggetto per analizzare i dati
			object = new JSONParser().parse(newString.toString());
			jObject = (JSONObject) object;
			/*
			 * parseJSON passa un JSONObject e crea una nuova HashMap di dati (jsonMap) per un facile recupero
			 * */
			parseJSON(jObject);	
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (org.json.simple.parser.ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // https://openweathermap.org/current per la JSON response
		cityName = jsonMap.get("name").toString();
		coordinates = (Map<String, Object>) jsonMap.get("coord");
		sysMap = (Map<String, Object>) jsonMap.get("sys");
		// l'alba e il tramonto vengono ritornati in Epoch time
		sunrise = new Date((long)(sysMap.get("sunrise")) * 1000);
		sunset = new Date((long)(sysMap.get("sunset")) * 1000);
		latitude = (double)coordinates.get("lat");
		longitude = (double) coordinates.get("lon");
		main = (Map<String, Object>) jsonMap.get("main");
		wind = (Map<String, Object>) jsonMap.get("wind");
		weatherArray = (JSONArray) jsonMap.get("weather");
		JSONObject w = (JSONObject) weatherArray.get(0);
		weather = toMap(w);
		//long temp = (int)main.get("temp");
		//int current_temp = (int)temp;
		
		//Setta gli attributi per la page data.jsp da riempire nel data table
		request.setAttribute("name", cityName);
		request.setAttribute("weather", weather.get("main"));
		request.setAttribute("current_temp", main.get("temp"));
		request.setAttribute("latitude", latitude);
		request.setAttribute("longitude", longitude);
		request.setAttribute("sunrise", df.format(sunrise));
		request.setAttribute("sunset", df.format(sunset));
		request.setAttribute("humidity", main.get("humidity"));
		
		//temp = (int)main.get("feels_like");
		//int feels_like = (int)temp;
		request.setAttribute("feels_like", main.get("feels_like"));
		request.setAttribute("high", main.get("temp_max"));
		request.setAttribute("low", main.get("temp_min"));
		request.setAttribute("wind_speed", wind.get("speed"));
		//request.setAttribute("degrees", wind.get("deg"));
		
		//Nuova richiesta API a weatherAPI.com per altri punti dati non forniti tramite openWeatherMap
		String weatherAPI_current = "http://api.weatherapi.com/v1/current.json?key=" + weatherAPI + "&q=" + zip;
		try {
			getData(weatherAPI_current);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			object = new JSONParser().parse(newString.toString());
			jObject = (JSONObject) object;
			parseJSON(jObject);	
		} catch (org.json.simple.parser.ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		weatherAPIlocation = (Map<String, Object>) jsonMap.get("location");
		weatherAPIcurrent = (Map<String, Object>) jsonMap.get("current");
		request.setAttribute("region", weatherAPIlocation.get("region"));
		request.setAttribute("pressure", weatherAPIcurrent.get("pressure_in"));
		request.setAttribute("uv", weatherAPIcurrent.get("uv"));
		request.setAttribute("visibility", weatherAPIcurrent.get("vis_miles"));
		request.setAttribute("degrees", weatherAPIcurrent.get("wind_dir"));
		request.setAttribute("CCapiKey", climaCellAPI);
		request.setAttribute("googleAPI", googleAPI);
		request.setAttribute("apiKey", API_KEY);
		request.setAttribute("mapBoxToken", mapBoxToken);
		
		//New API request a NOAA per le previsioni a 7 giorni
		noaaURL = "https://api.weather.gov/points/" + latitude + "," + longitude;
		try {
			getData(noaaURL);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			object = new JSONParser().parse(newString.toString());
			jObject = (JSONObject) object;
			parseJSON(jObject);	
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (org.json.simple.parser.ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        //Forward al data.jsp
        RequestDispatcher rd = request.getRequestDispatcher(destination);
        rd.forward(request, response);
    }
    
    public void getData(String urlToConnect) throws IOException {
    	url = new URL(urlToConnect);
        connect = url.openConnection();
        reader = new BufferedReader(new InputStreamReader(connect.getInputStream()));
        newString = new StringBuilder();
        while((line = reader.readLine()) != null) {
        	newString.append(line);
        }
    }
    
    public void parseJSON(JSONObject jObject) throws ParseException {
			jsonMap = toMap(jObject);		
    }
    
    public Map<String, Object> toMap(JSONObject jObject) {
		Map<String, Object> map = new HashMap<>();
		Iterator<String> iterator = jObject.keySet().iterator();
		while(iterator.hasNext()) {
			String key = iterator.next();
			Object value = jObject.get(key);
			
			if(value instanceof JSONArray) {
				value = (JSONArray) value;
			} else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
		}
    	
    	return map;
    	
    }
    

}
