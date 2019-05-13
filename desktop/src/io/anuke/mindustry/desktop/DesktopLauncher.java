package io.anuke.mindustry.desktop;

import io.anuke.arc.Files.FileType;
import io.anuke.arc.backends.lwjgl2.Lwjgl2Application;
import io.anuke.arc.backends.lwjgl2.Lwjgl2ApplicationConfiguration;
import io.anuke.mindustry.Mindustry;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.net.*;

public class DesktopLauncher{

    public static void main(String[] arg){
        try{
            Lwjgl2ApplicationConfiguration config = new Lwjgl2ApplicationConfiguration();
            config.title = "Mindustry";
            config.width = 960;
            config.height = 540;
            config.addIcon("sprites/icon.png", FileType.Internal);

            Platform.instance = new DesktopPlatform(arg);

            Net.setClientProvider(new ArcNetClient());
            Net.setServerProvider(new ArcNetServer());
            new Lwjgl2Application(new Mindustry(), config);
        }catch(Throwable e){
            DesktopPlatform.handleCrash(e);
        }
    }
}
