package io.anuke.mindustry.net;

import io.anuke.kryonet.NetSerializer;
import io.anuke.mindustry.net.Net.ClientProvider;
import io.anuke.mindustry.net.Net.SendMode;
import io.anuke.mindustry.net.Packets.Connect;
import io.anuke.mindustry.net.Packets.Disconnect;
import io.anuke.rudp.handlers.OrderedPacketHandler;
import io.anuke.rudp.rudp.RUDPClient;
import io.anuke.ucore.function.Consumer;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import static io.anuke.mindustry.Vars.netClient;
import static io.anuke.mindustry.Vars.threads;

public class RudpClient implements ClientProvider{
    final LZ4FastDecompressor decompressor = LZ4Factory.fastestInstance().fastDecompressor();
    final ByteBuffer buffer = ByteBuffer.allocate(4096);

    RUDPClient client;

    @Override
    public void connect(String ip, int port) throws IOException{
        if(client != null){
            disconnect();
        }

        client = new RUDPClient(InetAddress.getByName(ip), port);
        client.setPacketHandler(new OrderedPacketHandler(){
            @Override
            public void onConnection(){
                super.onConnection();
            }

            @Override
            public void onDisconnected(String reason, boolean local){
                threads.runDelay(() -> Net.handleClientReceived(new Disconnect()));
            }

            @Override
            public void handlePacket(byte[] data){
                synchronized(buffer){
                    Object o = NetSerializer.readBytes(buffer, data);

                    threads.runDelay(() -> {
                        try{
                            Net.handleClientReceived(o);
                        }catch (Exception e){
                            e.printStackTrace();
                            Net.showError("$text.server.mismatch");
                            netClient.disconnectQuietly();
                        }
                    });
                }
            }
        });
        client.connect();
        Connect c = new Connect();
        c.addressTCP = client.getAddress().getHostAddress();

        threads.runDelay(() -> Net.handleClientReceived(c));
    }

    @Override
    public void send(Object object, SendMode mode){
        if(client == null) return;
        synchronized(buffer){
            byte[] out = NetSerializer.writeBytes(buffer, object);

            if(mode == SendMode.udp){
                client.sendPacket(out);
            }else{
                client.sendReliablePacket(out);
            }
        }
    }

    @Override
    public void updatePing(){
    }

    @Override
    public int getPing(){
        if(client == null) return 0;
        return client.getLatency();
    }

    @Override
    public void disconnect(){
        if(client == null) return;
        client.disconnect();
        client = null;
    }

    @Override
    public byte[] decompressSnapshot(byte[] input, int size){
        byte[] result = new byte[size];
        decompressor.decompress(input, result);
        return result;
    }


    @Override
    public void discover(Consumer<Host> callback, Runnable done){
        //TODO
    }

    @Override
    public void pingHost(String address, int port, Consumer<Host> valid, Consumer<Exception> failed){
        //TODO
    }

    @Override
    public void dispose(){
        disconnect();
    }
}
