package io.anuke.mindustry.net;

import com.badlogic.gdx.utils.Array;
import io.anuke.kryonet.NetSerializer;
import io.anuke.mindustry.net.Net.SendMode;
import io.anuke.mindustry.net.Net.ServerProvider;
import io.anuke.mindustry.net.Packets.StreamBegin;
import io.anuke.mindustry.net.Packets.StreamChunk;
import io.anuke.rudp.rudp.RUDPClient;
import io.anuke.rudp.rudp.RUDPServer;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;

public class RudpServer implements ServerProvider{
    final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
    final ByteBuffer buffer = ByteBuffer.allocate(4096);
    final CopyOnWriteArrayList<RudpConnection> connections = new CopyOnWriteArrayList<>();
    final Array<RudpConnection> array = new Array<>();

    RUDPServer server;

    @Override
    public void host(int port) throws IOException{
        if(server != null){
            close();
        }
        server = new RUDPServer(port);
        server.start();
        connections.clear();
    }

    @Override
    public void sendStream(int id, Streamable stream){
        try{
            int cid;
            StreamBegin begin = new StreamBegin();
            begin.total = stream.stream.available();
            begin.type = Registrator.getID(stream.getClass());
            sendTo(id, begin, SendMode.tcp);
            cid = begin.id;

            while(stream.stream.available() > 0){
                byte[] bytes = new byte[Math.min(512, stream.stream.available())];
                stream.stream.read(bytes);

                StreamChunk chunk = new StreamChunk();
                chunk.id = cid;
                chunk.data = bytes;
                sendTo(id, chunk, SendMode.tcp);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    @Override
    public void send(Object object, SendMode mode){
        byte[] bytes = NetSerializer.writeBytes(buffer, object);
        for(NetConnection c : connections){
            sendRaw(c.id, bytes, mode);
        }
    }

    @Override
    public void sendTo(int id, Object object, SendMode mode){
        byte[] bytes = NetSerializer.writeBytes(buffer, object);
        sendRaw(id, bytes, mode);
    }

    @Override
    public void sendExcept(int id, Object object, SendMode mode){
        byte[] bytes = NetSerializer.writeBytes(buffer, object);
        for(NetConnection c : connections){
            if(c.id != id){
                sendRaw(c.id, bytes, mode);
            }
        }
    }

    void sendRaw(int id, byte[] data, SendMode mode){
        if(mode == SendMode.tcp){
            server.getClient(id).sendReliablePacket(data);
        }else{
            server.getClient(id).sendPacket(data);
        }
    }

    @Override
    public void close(){
        if(server == null) return;

        server.stop();
        server = null;
    }

    @Override
    public byte[] compressSnapshot(byte[] input){
        return compressor.compress(input);
    }

    @Override
    public Array<? extends NetConnection> getConnections(){
        array.clear();
        for(RudpConnection c : connections){
            array.add(c);
        }
        return array;
    }

    @Override
    public NetConnection getByID(int id){
        for(NetConnection n : connections){
            if(n.id == id){
                return n;
            }
        }
        return null;
    }

    @Override
    public void dispose(){
        close();
    }

    class RudpConnection extends NetConnection{

        public RudpConnection(RUDPClient client, String address){
            super(client.getID(), address);
        }

        @Override
        public void send(Object object, SendMode mode){
        }

        @Override
        public void close(){

        }
    }
}
