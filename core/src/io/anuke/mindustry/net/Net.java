package io.anuke.mindustry.net;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net.HttpRequest;
import com.badlogic.gdx.Net.HttpResponse;
import com.badlogic.gdx.Net.HttpResponseListener;
import com.badlogic.gdx.net.HttpRequestBuilder;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.net.Packets.KickReason;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.function.BiConsumer;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Pooling;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

@SuppressWarnings("unchecked")
public class Net{
    private boolean server;
    private boolean active;
    private boolean clientLoaded;
    private Array<Object> packetQueue = new Array<>();
    private ObjectMap<Class<?>, Consumer> clientListeners = new ObjectMap<>();
    private ObjectMap<Class<?>, BiConsumer<Integer, Object>> serverListeners = new ObjectMap<>();
    private Client clientProvider = new Client();
    private Server serverProvider = new Server();

    private final LZ4FastDecompressor decompressor = LZ4Factory.fastestInstance().fastDecompressor();
    private final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();

    /**Display a network error. Call on the graphics thread.*/
    public void showError(Throwable e){

        if(!headless){

            Throwable t = e;
            while(t.getCause() != null){
                t = t.getCause();
            }

            String error = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            String type = t.getClass().toString().toLowerCase();

            if(error.equals("mismatch")){
                error = Bundles.get("text.error.mismatch");
            }else if(error.contains("port out of range") || error.contains("invalid argument") || (error.contains("invalid") && error.contains("address"))){
                error = Bundles.get("text.error.invalidaddress");
            }else if(error.contains("connection refused") || error.contains("route to host") || type.contains("unknownhost")){
                error = Bundles.get("text.error.unreachable");
            }else if(type.contains("timeout")){
                error = Bundles.get("text.error.timedout");
            }else if(error.equals("alreadyconnected")){
                error = Bundles.get("text.error.alreadyconnected");
            }else if(!error.isEmpty()){
                error = Bundles.get("text.error.any");
            }

            ui.showText("", Bundles.format("text.connectfail", error));
            ui.loadfrag.hide();

            if(client()){
                netClient.disconnectQuietly();
            }
        }

        Log.err(e);
    }

    /**Sets the client loaded status, or whether it will recieve normal packets from the server.*/
    public void setClientLoaded(boolean loaded){
        clientLoaded = loaded;

        if(loaded){
            //handle all packets that were skipped while loading
            for(int i = 0; i < packetQueue.size; i++){
                Log.info("Processing {0} packet post-load.", packetQueue.get(i).getClass());
                handleClientReceived(packetQueue.get(i));
            }
        }
        //clear inbound packet queue
        packetQueue.clear();
    }

    /**Connect to an address.*/
    public void connect(String ip, int port, Runnable success){
        if(!active){
            clientProvider.connect(ip, port, this::showError, success);
            active = true;
            server = false;
        }else{
            showError(new IOException("alreadyconnected"));
        }
    }

    /**Host a server at an address.*/
    public void host(int port) throws IOException{
        serverProvider.host(port);
        active = true;
        server = true;

        Timers.runTask(60f, Platform.instance::updateRPC);
    }

    /**Closes the server.*/
    public void closeServer(){
        for(NetConnection con : getConnections()){
            Call.onKick(con.id, KickReason.serverClose);
        }

        serverProvider.close();
        server = false;
        active = false;
    }

    public void disconnect(){
        clientProvider.disconnect();
        server = false;
        active = false;
    }

    public byte[] compressSnapshot(byte[] input){
        return compressor.compress(input);
    }

    public byte[] decompressSnapshot(byte[] input, int size){
        byte[] result = new byte[size];
        decompressor.decompress(input, result);
        return result;
    }

    /**
     * Starts discovering servers on a different thread.
     * Callback is run on the main libGDX thread.
     */
    public void discoverServers(Consumer<Host> cons, Runnable done){
        clientProvider.discover(cons, done);
    }

    /**Returns a list of all connections IDs.*/
    public Array<NetConnection> getConnections(){
        return (Array<NetConnection>) serverProvider.getConnections();
    }

    /**Returns a connection by ID*/
    public NetConnection getConnection(int id){
        return serverProvider.getByID(id);
    }

    /**Send an object to all connected clients, or to the server if this is a client.*/
    public void send(Object object, SendMode mode){
        if(server){
            if(serverProvider != null) serverProvider.send(object, mode);
        }else{
            if(clientProvider != null) clientProvider.send(object, mode);
        }
    }

    /**Send an object to a certain client. Server-side only*/
    public void sendTo(int id, Object object, SendMode mode){
        serverProvider.sendTo(id, object, mode);
    }

    /**Send an object to everyone EXCEPT certain client. Server-side only*/
    public void sendExcept(int id, Object object, SendMode mode){
        serverProvider.sendExcept(id, object, mode);
    }

    /**Registers a client listener for when an object is recieved.*/
    public <T> void handleClient(Class<T> type, Consumer<T> listener){
        clientListeners.put(type, listener);
    }

    /**Registers a server listener for when an object is recieved.*/
    public <T> void handleServer(Class<T> type, BiConsumer<Integer, T> listener){
        serverListeners.put(type, (BiConsumer<Integer, Object>) listener);
    }

    /**Call to handle a packet being recieved for the client.*/
    public void handleClientReceived(Object object){
        if(clientListeners.get(object.getClass()) != null){

            if(clientLoaded || ((object instanceof Packet) && ((Packet) object).isImportant())){
                if(clientListeners.get(object.getClass()) != null)
                    clientListeners.get(object.getClass()).accept(object);
                Pooling.free(object);
            }else if(!((object instanceof Packet) && ((Packet) object).isUnimportant())){
                packetQueue.add(object);
                Log.info("Queuing packet {0}", object);
            }else{
                Pooling.free(object);
            }
        }else{
            Log.err("Unhandled packet type: '{0}'!", object);
        }
    }

    /**Call to handle a packet being recieved for the server.*/
    public void handleServerReceived(int connection, Object object){

        if(serverListeners.get(object.getClass()) != null){
            if(serverListeners.get(object.getClass()) != null)
                serverListeners.get(object.getClass()).accept(connection, object);
            Pooling.free(object);
        }else{
            Log.err("Unhandled packet type: '{0}'!", object.getClass());
        }
    }

    /**Pings a host in an new thread. If an error occured, failed() should be called with the exception.*/
    public void pingHost(String address, int port, Consumer<Host> valid, Consumer<Exception> failed){
        clientProvider.pingHost(address, port, valid, failed);
    }
    
    public void update(){
        clientProvider.update();
        serverProvider.update();
    }

    /**Get the client ping. Only valid after updatePing().*/
    public int getPing(){
        return server() ? 0 : clientProvider.getPing();
    }

    /**Whether the net is active, e.g. whether this is a multiplayer game.*/
    public boolean active(){
        return active;
    }

    /**Whether this is a server or not.*/
    public boolean server(){
        return server && active;
    }

    /**Whether this is a client or not.*/
    public boolean client(){
        return !server && active;
    }

    public void dispose(){
        if(clientProvider != null) clientProvider.disconnect();
        if(serverProvider != null) serverProvider.close();
        clientProvider = null;
        serverProvider = null;
        server = false;
        active = false;
    }

    public void http(String url, String method, Consumer<String> listener, Consumer<Throwable> failure){
        http(url, method, null, listener, failure);
    }

    public void http(String url, String method, String body, Consumer<String> listener, Consumer<Throwable> failure){
        HttpRequest req = new HttpRequestBuilder().newRequest()
        .method(method).url(url).content(body).build();

        Gdx.net.sendHttpRequest(req, new HttpResponseListener(){
            @Override
            public void handleHttpResponse(HttpResponse httpResponse){
                listener.accept(httpResponse.getResultAsString());
            }

            @Override
            public void failed(Throwable t){
                failure.accept(t);
            }

            @Override
            public void cancelled(){
            }
        });
    }

    public enum SendMode{
        reliable, unreliable
    }
}
