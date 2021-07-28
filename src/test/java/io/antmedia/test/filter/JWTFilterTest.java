package io.antmedia.test.filter;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.servlet.ServletException;

import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import io.antmedia.AppSettings;
import io.antmedia.filter.JWTFilter;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.InputStreamReader;
import java.io.BufferedReader;

public class JWTFilterTest {
	
	protected static Logger logger = LoggerFactory.getLogger(JWTFilterTest.class);
	
    @Test
    public void testDoFilterPass() throws IOException, ServletException, ParseException {
    	   	
    	JWTFilter jwtFilter = Mockito.spy(new JWTFilter());
        
        MockHttpServletResponse httpServletResponse;
        MockHttpServletRequest httpServletRequest;
        MockFilterChain filterChain;
        
        AppSettings appSettings = new AppSettings();
        appSettings.setJwtSecretKey("testtesttesttesttesttesttesttest");       
        appSettings.setJwtControlEnabled(true);
        
        Mockito.doReturn(appSettings).when(jwtFilter).getAppSettings();
        
		String token = JWT.create().sign(Algorithm.HMAC256(appSettings.getJwtSecretKey()));
		String invalidToken = JWT.create().sign(Algorithm.HMAC256("invalid-key-invalid-key-invalid-key"));
		
		System.out.println("Valid Token: " + token);

        // JWT Token enable and invalid token scenario
        {   
        	//reset filterchain
        	filterChain = new MockFilterChain();
        	
        	//reset httpServletResponse
        	httpServletResponse = new MockHttpServletResponse();
        	
        	//reset httpServletRequest
        	httpServletRequest = new MockHttpServletRequest();
        	
            appSettings.setJwtControlEnabled(true);
            
            Mockito.doReturn(appSettings).when(jwtFilter).getAppSettings();
            
            httpServletRequest.addHeader("Authorization", invalidToken);

            jwtFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
            assertEquals(HttpStatus.FORBIDDEN.value(),httpServletResponse.getStatus());
        }
        
        // JWT Token disable and passed token scenario
        {
        	//reset filterchains
        	filterChain = new MockFilterChain();
        	
        	//reset httpServletResponses
        	httpServletResponse = new MockHttpServletResponse();
        	
        	//reset httpServletRequest
        	httpServletRequest = new MockHttpServletRequest();
        	
            appSettings.setJwtControlEnabled(false);
            
            Mockito.doReturn(appSettings).when(jwtFilter).getAppSettings();
            
            httpServletRequest.addHeader("Authorization", token);
            
            jwtFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
            assertEquals(HttpStatus.OK.value(),httpServletResponse.getStatus()); 
        }
        
        // JWT Token enable and valid token scenario
        {
        	//reset filterchains
        	filterChain = new MockFilterChain();
        	
        	//reset httpServletResponses
        	httpServletResponse = new MockHttpServletResponse();
        	
        	//reset httpServletRequest
        	httpServletRequest = new MockHttpServletRequest();
        	
            appSettings.setJwtControlEnabled(true);
            
            Mockito.doReturn(appSettings).when(jwtFilter).getAppSettings();
            
            httpServletRequest.addHeader("Authorization", token);
            
            jwtFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
            assertEquals(HttpStatus.OK.value(),httpServletResponse.getStatus());
        }
        
        // JWT Token enable and null header token scenario
        {
        	//reset filterchains
        	filterChain = new MockFilterChain();
        	
        	//reset httpServletResponses
        	httpServletResponse = new MockHttpServletResponse();
        	
        	//reset httpServletRequest
        	httpServletRequest = new MockHttpServletRequest();
        	
            appSettings.setJwtControlEnabled(true);
            
            Mockito.doReturn(appSettings).when(jwtFilter).getAppSettings();
            
            jwtFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
            assertEquals(HttpStatus.FORBIDDEN.value(),httpServletResponse.getStatus());
        }        
        // JWKS Token enable and valid token scenario
        {
        	//reset filterchains
        	filterChain = new MockFilterChain();
        	
        	//reset httpServletResponses
        	httpServletResponse = new MockHttpServletResponse();
        	
        	//reset httpServletRequest
        	httpServletRequest = new MockHttpServletRequest();
        	
        	appSettings.setJwksURL("https://antmedia.us.auth0.com");
        	appSettings.setJwtControlEnabled(true);
        	
        	token = getTokenJWKS();

            Mockito.doReturn(appSettings).when(jwtFilter).getAppSettings();
            
            httpServletRequest.addHeader("Authorization", token);
            
            jwtFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
            assertEquals(HttpStatus.OK.value(),httpServletResponse.getStatus());
        }
        
        // JWKS Token enable and valid token scenario
        {
        	//reset filterchains
        	filterChain = new MockFilterChain();
        	
        	//reset httpServletResponses
        	httpServletResponse = new MockHttpServletResponse();
        	
        	//reset httpServletRequest
        	httpServletRequest = new MockHttpServletRequest();
        	
        	appSettings.setJwksURL("https://invalidjwksurl.com");
        	appSettings.setJwtControlEnabled(true);
        	
        	token = getTokenJWKS();

            Mockito.doReturn(appSettings).when(jwtFilter).getAppSettings();
            
            httpServletRequest.addHeader("Authorization", token);
            
            jwtFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
            assertEquals(HttpStatus.FORBIDDEN.value(),httpServletResponse.getStatus());
        }
        
    }

    public String getTokenJWKS() throws ClientProtocolException, IOException, ParseException{
    	
    	String token;
    	
    	String url = "https://antmedia.us.auth0.com/oauth/token";

    		  HttpClient client =  HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();
    		  
                HttpUriRequest post;
    				post = RequestBuilder.post().setUri(url)
    				        .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
    				        .setEntity(new StringEntity("{\"client_id\":\"7QQ3Y9KK2OcWNK4p1zrte5S41IdxjlKs\",\"client_secret\":\"4Hr7PWwrTf6YFynkO5QeNQrlxe5r7HtfUdLhis2i_vbXdtF1VI0SwnP0ZSlhf0Yh\",\"audience\":\"https://antmedia.us.auth0.com/api/v2/\",\"grant_type\":\"client_credentials\"}")).build();


                HttpResponse response = client.execute(post);

                StringBuffer result  = readResponse(response);
                System.out.println("result string: " + result.toString());
                
    			JSONObject appsJSON = (JSONObject) new JSONParser().parse(result.toString());
    			token = (String) appsJSON.get("access_token");
               
    	  return token;
    	
    }

    public static StringBuffer readResponse(HttpResponse response) throws IOException {
    	StringBuffer result = new StringBuffer();

    	if(response.getEntity() != null) {
    		BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

    		String line = "";
    		while ((line = rd.readLine()) != null) {
    			result.append(line);
    		}
    	}
    	return result;
    }

    }
