package io.anuke.mindustry.net;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import io.anuke.hexnet.*;
import io.anuke.mindustry.net.Net.ClientProvider;
import io.anuke.mindustry.net.Net.SendMode;
import io.anuke.mindustry.net.Packets.Connect;
import io.anuke.mindustry.net.Packets.Disconnect;
import io.anuke.ucore.function.Consumer;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;

import static io.anuke.mindustry.Vars.*;

public class ClientImpl implements ClientProvider{
    final LZ4FastDecompressor decompressor = LZ4Factory.fastestInstance().fastDecompressor();
    final HexClient client = new HexClient();

    Consumer<Host> lastCallback;
    Array<InetAddress> foundAddresses = new Array<>();

    @Override
    public void connect(String ip, int port) throws IOException{
        client.setSerializer(new SerializerImpl());
        client.setDiscovery((buffer, socketAddress) -> {
            Host host = NetworkIO.readServerData(socketAddress.getAddress().getHostAddress(), buffer);
            for(InetAddress address : foundAddresses){
                if(address.equals(socketAddress.getAddress()) || (isLocal(address) && isLocal(socketAddress.getAddress()))){
                    return;
                }
            }
            Gdx.app.postRunnable(() -> lastCallback.accept(host));
            foundAddresses.add(socketAddress.getAddress());
        });

        client.setListener(new HexClientListener(){
            @Override
            public void connected(){
                threads.runDelay(() -> Net.handleClientReceived(new Connect()));
            }

            @Override
            public void disconnected(Reason reason){
                threads.runDelay(() -> Net.handleClientReceived(new Disconnect()));
            }

            @Override
            public void recieved(Object object){
                threads.runDelay(() -> {
                    try{
                        Net.handleClientReceived(object);
                    }catch(Exception e){
                        e.printStackTrace();
                        Net.showError("$text.server.mismatch");
                        netClient.disconnectQuietly();
                    }
                });
            }

            @Override
            public void error(Throwable t){
                t.printStackTrace();
            }
        });

        client.connect(InetAddress.getByName(ip), port);
    }

    @Override
    public void send(Object object, SendMode mode){
        client.send(object, mode == SendMode.unreliable ? Mode.unreliable : Mode.reliable);
    }

    @Override
    public void updatePing(){
    }

    @Override
    public int getPing(){
        return (int)client.getLatency();
    }

    @Override
    public void disconnect(){
        client.disconnect();
    }

    @Override
    public byte[] decompressSnapshot(byte[] input, int size){
        byte[] result = new byte[size];
        decompressor.decompress(input, result);
        return result;
    }


    @Override
    public void discover(Consumer<Host> callback, Runnable done){
        runAsync(() -> {
            foundAddresses.clear();
            lastCallback = callback;
            client.discoverHosts(port, 3000);
            Gdx.app.postRunnable(done);
        });
    }

    @Override
    public void pingHost(String address, int port, Consumer<Host> valid, Consumer<Exception> failed){
        runAsync(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.send(new DatagramPacket(new byte[]{PacketType.DISCOVER}, 1, InetAddress.getByName(address), port));
                socket.setSoTimeout(2000);

                lastCallback = valid;

                DatagramPacket packet = new DatagramPacket(new byte[HexNet.MAX_DISCOVERY_PACKET_SIZE], HexNet.MAX_DISCOVERY_PACKET_SIZE);
                socket.receive(packet);

                ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
                Host host = NetworkIO.readServerData(packet.getAddress().getHostAddress(), buffer);

                Gdx.app.postRunnable(() -> valid.accept(host));
            } catch (Exception e) {
                Gdx.app.postRunnable(() -> failed.accept(e));
            }
        });
    }

    @Override
    public void dispose(){
        disconnect();
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
