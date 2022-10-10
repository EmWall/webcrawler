package com.eulerity.hackathon.myrunnable;

import com.eulerity.hackathon.imagefinder.ImageFinder;

public class MyRunnable implements Runnable{
    private Integer ID;
    private String URL;
    private Thread THREAD;
    public MyRunnable(Integer id){
        this.ID = id;
    }
    public void run(){
        ImageFinder.getURLs(URL);
    }
    public Integer getID(){
        return ID;
    }
    public void setParams(String url){
        this.URL = url;
    }
    public void createThread(){
        THREAD = new Thread(this);
        // System.out.println("Thread " + ID + " started");
		THREAD.start();
    }
    public String getState(){
        if(THREAD == null){
            return "NONE CREATED";
        }
        return THREAD.getState().toString();
    }
}
