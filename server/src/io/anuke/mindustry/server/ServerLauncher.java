package io.anuke.mindustry.server;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.util.EmptyLogger;

public class ServerLauncher extends HeadlessApplication{

    public ServerLauncher(ApplicationListener listener, HeadlessApplicationConfiguration config){
        super(listener, config);

        //don't do anything at all for GDX logging: don't want controller info and such
        Gdx.app.setApplicationLogger(new EmptyLogger());
    }

    public static void main(String[] args){
        try{

            HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
            Settings.setPrefHandler((appName) -> Gdx.files.local("config"));

            new ServerLauncher(new MindustryServer(args), config);
        }catch(Throwable t){
            CrashHandler.handle(t);
        }

        //find and handle uncaught exceptions in libGDX thread
        for(Thread thread : Thread.getAllStackTraces().keySet()){
            if(thread.getName().equals("HeadlessApplication")){
                thread.setUncaughtExceptionHandler((t, throwable) -> {
                    CrashHandler.handle(throwable);
                    System.exit(-1);
                });
                break;
            }
        }
    }
}