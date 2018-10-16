package io.anuke.mindustry.server;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.RudpClient;
import io.anuke.mindustry.net.RudpServer;
import io.anuke.ucore.util.EmptyLogger;
import io.anuke.ucore.util.OS;

public class ServerLauncher extends HeadlessApplication{

    public ServerLauncher(ApplicationListener listener, HeadlessApplicationConfiguration config){
        super(listener, config);

        //don't do anything at all for GDX logging: don't want controller info and such
        Gdx.app.setApplicationLogger(new EmptyLogger());
    }

    public static void main(String[] args){
        try{

            Net.setClientProvider(new RudpClient());
            Net.setServerProvider(new RudpServer());

            HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
            config.preferencesDirectory = OS.getAppDataDirectoryString("Mindustry");

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