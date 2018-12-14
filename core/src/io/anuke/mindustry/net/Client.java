package io.anuke.mindustry.net;

import com.badlogic.gdx.Gdx;
import io.anuke.mindustry.net.Net.SendMode;
import io.anuke.mindustry.net.Packets.Connect;
import io.anuke.mindustry.net.Packets.Disconnect;
import io.anuke.ucore.function.Consumer;
import ru.maklas.mnet2.ResponseType;
import ru.maklas.mnet2.Socket;
import ru.maklas.mnet2.SocketImpl;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

import static io.anuke.mindustry.Vars.*;

public class Client{
    Socket socket;

    public void connect(String ip, int port, Consumer<IOException> error, Runnable success){
        try{
            socket = new SocketImpl(InetAddress.getByName(ip), port, new NetSerializer());
            socket.addDcListener((sock, reason) -> threads.runDelay(() -> net.handleClientReceived(new Disconnect())));
            socket.connectAsync(null, 2000, response -> {
                if(response.getType() == ResponseType.ACCEPTED){
                    Gdx.app.postRunnable(() -> {
                        success.run();
                        net.handleClientReceived(new Connect());
                    });
                }else if(response.getType() == ResponseType.WRONG_STATE){
                    Gdx.app.postRunnable(() -> error.accept(new IOException("alreadyconnected")));
                }else{
                    Gdx.app.postRunnable(() -> error.accept(new IOException("connection refused")));
                }
            });
        }catch(IOException e){
            error.accept(e);
        }
    }

    public void send(Object object, SendMode mode){
        if(mode == SendMode.reliable){
            socket.send(object);
        }else{
            socket.sendUnreliable(object);
        }
    }

    public void update(){
        if(socket == null) return;

        socket.update((sock, object) -> Gdx.app.postRunnable(() -> {
            try{
                net.handleClientReceived(object);
            }catch(Exception e){
                net.showError(e);
                netClient.disconnectQuietly();
            }
        }));
    }

    public int getPing(){
        return socket == null ? 0 : (int)socket.getPing();
    }

    public void disconnect(){
        if(socket != null) socket.close();
    }

    public void discover(Consumer<Host> callback, Runnable done){

        runAsync(() -> {
            byte[] bytes = new byte[512];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
            ArrayList<InetAddress> foundAddresses = new ArrayList<>();

            try(DatagramSocket socket = new DatagramSocket()){
                broadcast(port, socket);

                socket.setSoTimeout(4000);

                outer:
                while(true){

                    try{
                        socket.receive(packet);
                    }catch(SocketTimeoutException ex){
                        done.run();
                        return;
                    }

                    buffer.position(0);

                    InetAddress address = ((InetSocketAddress)packet.getSocketAddress()).getAddress();

                    for(InetAddress other : foundAddresses){
                        if(other.equals(address) || (isLocal(other) && isLocal(address))){
                            continue outer;
                        }
                    }

                    Host host = NetworkIO.readServerData(address.getHostName(), buffer);
                    callback.accept(host);
                    foundAddresses.add(address);
                }
            }catch(IOException ex){
                done.run();
            }
        });
    }

    public void pingHost(String address, int port, Consumer<Host> valid, Consumer<Exception> failed){
        runAsync(() -> {
            try{
                DatagramPacket packet = new DatagramPacket(new byte[512], 512);

                DatagramSocket socket = new DatagramSocket();
                socket.send(new DatagramPacket(new byte[]{-2}, 1, InetAddress.getByName(address), port));
                socket.setSoTimeout(4000);
                socket.receive(packet);

                ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
                Host host = NetworkIO.readServerData(packet.getAddress().getHostAddress(), buffer);

                Gdx.app.postRunnable(() -> valid.accept(host));
            }catch(Exception e){
                e.printStackTrace();
                Gdx.app.postRunnable(() -> failed.accept(e));
            }
        });
    }
    private void broadcast (int udpPort, DatagramSocket socket) throws IOException{
        byte[] data = {-2};

        for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())){
            for (InetAddress address : Collections.list(iface.getInetAddresses())){

                byte[] ip = address.getAddress(); //255.255.255.255
                try{
                    socket.send(new DatagramPacket(data, data.length, InetAddress.getByAddress(ip), udpPort));
                }catch (Exception ignored){}
                ip[3] = -1; //255.255.255.0
                try{
                    socket.send(new DatagramPacket(data, data.length, InetAddress.getByAddress(ip), udpPort));
                }catch (Exception ignored){}
                ip[2] = -1; //255.255.0.0
                try{
                    socket.send(new DatagramPacket(data, data.length, InetAddress.getByAddress(ip), udpPort));
                }catch (Exception ignored){}
            }
        }
    }

    private void runAsync(Runnable run){
        Thread thread = new Thread(run, "Client Async Run");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean isLocal(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) return true;

        try {
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
