package com.eulerity.hackathon.imagefinder;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.eulerity.hackathon.myrunnable.MyRunnable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@WebServlet(
    name = "ImageFinder",
    urlPatterns = {"/main"}
)
public class ImageFinder extends HttpServlet{
	private static final long serialVersionUID = 1L;	//provided

	private static final int MAX_DEPTH = 5;	//for use in dfs of urls
	
	private static final int MAX_THREADS = 7;	//for use in dfs of urls
	
	protected static final Gson GSON = new GsonBuilder().create();	//provided

	//Multithreading safe place to store URLs of pages to be visited
	protected static final Set<String> allURLs = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());	
	
	//Multithreading safe place to store URLs of pages that have been visited
	protected static final Set<String> visited = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());	

	//Multithreading safe place to store URLs of images
	protected static final List<String> images = Collections.synchronizedList(new ArrayList<String>());

	protected static final List<MyRunnable> threads = new ArrayList<MyRunnable>();	//List of MyRunnable objects to pass params to threads

	protected static String HOST_NAME;	//Host name of starting URL to compare against subsequent links

	//This is just a test array
	public static final String[] testImages = {
			"https://images.pexels.com/photos/545063/pexels-photo-545063.jpeg?auto=compress&format=tiny",
			"https://images.pexels.com/photos/464664/pexels-photo-464664.jpeg?auto=compress&format=tiny",
			"https://images.pexels.com/photos/406014/pexels-photo-406014.jpeg?auto=compress&format=tiny",
			"https://images.pexels.com/photos/1108099/pexels-photo-1108099.jpeg?auto=compress&format=tiny"
  };


	@Override
	protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//provided set up code; gets URL and path
		resp.setContentType("text/json");
		String path = req.getServletPath();
		String url = req.getParameter("url");
		System.out.println("Got request of:" + path + " with query param:" + url);
		
		//Create imgs as Object[]
		Object[] imgs;
		//if URL is valid -> imgs will contain the contents of images after crawl is performed
		if(isValidURL(url, false)){
			imgs = crawl(url);
		}
		//if URL is not valid imgs gets provided test_images
		else{
			imgs = testImages;
		}
		
		//provided code to post images to page
		resp.getWriter().print(GSON.toJson(imgs));
		System.out.println("I'm done");
	}

	//Ensure URLs are valid and check domain
	private static boolean isValidURL(String url, Boolean checkDomain){
		//Check for null and email
		if(url == null || url.startsWith("mail") || url.contains("nofollow") || url.contains("pdf")){
			return false;
		}
		//attempt to validate url by making it URI
		try{
			URI uri = new URL(url).toURI();
			//if domain is to be checked, check if url has same host name
			if(checkDomain){
				String host = uri.getHost();
				if(host.startsWith("www.")){
					host = host.substring(4);
				}
				if(!host.equals(HOST_NAME)){
					return false;
				}
			}
			return true;
		}

		//if exceptions were thrown, URL is not valid
		catch (Exception e){
			return false;
		}
	}

	//Wrapper method for setup and thread creation
	private final Object[] crawl(String url){
		//set HOST_NAME from url
		try{
			URI uri = new URL(url).toURI();
			HOST_NAME = uri.getHost();
			if(HOST_NAME.startsWith("www.")){
				HOST_NAME = HOST_NAME.substring(4);
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		//Create all MyRunnables
		for(int i = 0; i <= MAX_THREADS; i++){
			MyRunnable r = new MyRunnable(i);
			threads.add(r);
		}

		//Start threads to call getURLs for each URL in allURLs for 5 levels
		allURLs.add(url);
		for(int i = 0; i < MAX_DEPTH; i++){
			System.out.println("Depth = " + i);
			int size = allURLs.size();
			for(int j = 0; j < size; j++){
				String u = allURLs.iterator().next();
				giveThread(u);
				allURLs.remove(u);
			}
			waitForURLs();
		}

		//Wait for all threads to finish and return
		waitForURLs();
		System.out.println(allURLs.size() + " links, " + images.size() + " images");
		return images.toArray();
	}

	//get valid URLs of images on page
	//get all valid URLs to other pages within same domain
	public static void getURLs(String url){
		if(isValidURL(url, true)){
			allURLs.add(url);
			try{
				TimeUnit.SECONDS.sleep(1);
				Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0").referrer("http://www.google.com").get();
				visited.add(url);
				
				//Get images
				Elements imgURLs = doc.select("img[src~=(?i)\\.(png|jpe?g|gif)]");
				for(Element image : imgURLs){
					String imageURL = image.attr("src");
					if(!images.contains(imageURL) && isValidURL(imageURL, false)){
						images.add(imageURL);
					}
					//handle images hosted on current url
					else {
						imageURL = "http://www." + HOST_NAME + imageURL;
						if(!images.contains(imageURL) && isValidURL(imageURL, false)){
							images.add(imageURL);
						}
					}
				}

				//Get links
				Elements links = doc.select("a[href]");
				for(Element link : links){
					String newURL = link.attr("abs:href");
					//assign threads to next level of links
					if(!allURLs.contains(newURL) && !visited.contains(newURL) && isValidURL(newURL, true)){
						allURLs.add(newURL);
					}
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	private static void waitForURLs(){
		try{
			for(MyRunnable thread : threads){
				while(thread.getState() != "TERMINATED" && thread.getState() != "NONE CREATED"){
					TimeUnit.SECONDS.sleep(1);
				}
			}
			TimeUnit.SECONDS.sleep(1);
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

	//Assign threads to urls 
	public static void giveThread(String url){
		MyRunnable thread = null;
		//Find idle MyRunnable 
		while(thread == null){
			for(MyRunnable thr : threads){
				if(thr.getState() == "NONE CREATED" || thr.getState() == "TERMINATED"){
					thread = thr;
					break;
				}
			}
		}

		//Set params and start
		thread.setParams(url);
		thread.createThread();
	}
}
