package io.antmedia;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import org.openqa.selenium.NoAlertPresentException;

public class BrowserStreamMerger {
    protected static Logger logger = LoggerFactory.getLogger(BrowserStreamMerger.class);
    protected WebDriver driver;
    protected String url;
    public boolean driverRunning = false;
    protected String roomId;

    public BrowserStreamMerger(String roomId){
        this.url = "http://localhost:5080/LiveApp/merge_streams.html?roomName=room1";
        WebDriverManager.chromedriver().setup();
        this.roomId = roomId;
    }
    public BrowserStreamMerger(String roomId, String url){
        this.url = url;
        this.roomId = roomId;
        WebDriverManager.chromedriver().setup();
    }

    public void init(){

        logger.info("Starting to initialize headless chrome");
        ChromeOptions chrome_options = new ChromeOptions();
        chrome_options.addArguments("--disable-extensions");
        chrome_options.addArguments("--disable-gpu");
        chrome_options.addArguments("--headless");
        chrome_options.addArguments("--no-sandbox");
        this.driver = new ChromeDriver(chrome_options);
        this.driverRunning = true;
        this.driver.get(this.url);
        String title = this.driver.getTitle();
        System.out.println(this.url + " " + this.driver + " " + title);
        if(this.driver != null && title.equalsIgnoreCase("Ant Media Server WebRTC Conference Room Merge")){
            delay(3);
            logger.info("Joining the room and merging streams");
            run();
        }else{
            logger.warn("Did not find correct address or driver is not initialized properly");
        }
    }
    public void run(){
        this.driver.findElement(By.xpath("//*[@id='join_publish_button']")).click();
        if(checkAlert() == false){
            stop();
        }
        delay(3);
    }
    public String getRoomId(){
        return this.roomId;
    }
    public void stop(){
        delay(3);
        logger.info("Closing the driver");
        this.driver.quit();
        this.driverRunning = false;
    }
    public boolean isRunning(){
        return this.driverRunning;
    }

    public boolean checkAlert()
    {
        try
        {
            String alert = this.driver.switchTo().alert().getText();
            System.out.println(alert);
            if(alert.equalsIgnoreCase("HighResourceUsage")){
                logger.error("High resource usage blocks merging");
                this.driver.switchTo().alert().accept();
                return false;
            }
            else if(alert.equalsIgnoreCase("There is no stream available in the room")){
                logger.info("No stream available to rebroadcast");
                this.driver.switchTo().alert().accept();
                return false;
            }
            else{
                logger.error("Unexpected pop-up alert on browser = {}" , alert);
                this.driver.switchTo().alert().dismiss();
                return false;
            }
        }
        catch (NoAlertPresentException e)
        {
            return true;
        }
    }

    public void delay(int seconds){
        try {
            TimeUnit.SECONDS.sleep(seconds);
        }catch(Exception e) {
            logger.warn("Delay is interrupted, destroying process");
            stop();
        }
    }
}
