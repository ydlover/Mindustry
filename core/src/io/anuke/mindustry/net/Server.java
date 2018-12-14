package io.anuke.mindustry.net;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.net.Net.SendMode;
import io.anuke.mindustry.net.Packets.Connect;
import io.anuke.mindustry.net.Packets.Disconnect;
import io.anuke.ucore.util.Log;
import ru.maklas.mnet2.ServerSocket;
import ru.maklas.mnet2.Socket;
import ru.maklas.mnet2.SocketState;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.anuke.mindustry.Vars.net;

public class Server{
    final CopyOnWriteArrayList<ConnectionImpl> connections = new CopyOnWriteArrayList<>();
    final Array<ConnectionImpl> array = new Array<>();
    ServerSocket socket;
    int lastID;

    public void host(int port) throws IOException{
        socket = new ServerSocket(port, con -> {
            Socket sock = con.accept(null);

            ConnectionImpl kn = new ConnectionImpl(sock);
            sock.setUserData(kn);

            String ip = sock.getRemoteAddress().getHostAddress();

            Connect c = new Connect();
            c.id = kn.id;
            c.addressTCP = ip;

            Log.info("&bRecieved connection: {0} / {1}", c.id, c.addressTCP);

            connections.add(kn);
            Gdx.app.postRunnable(() -> net.handleServerReceived(kn.id, c));

            sock.addDcListener((socket, message) -> {
                Log.info("&bLost connection {0}. Reason: {1}", kn.id, message);

                Disconnect dc = new Disconnect();
                dc.id = kn.id;

                Gdx.app.postRunnable(() -> {
                    net.handleServerReceived(kn.id, dc);
                    connections.remove(kn);
                });
            });
        }, NetSerializer::new, () -> {
            DatagramPacket packet = new DatagramPacket(new byte[512], 512);
            ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
            NetworkIO.writeServerData(buffer);
            return packet;
        });

        connections.clear();
    }

    public void update(){
        if(socket == null) return;

        socket.update();
        for(Socket socket : socket.getSockets()){
            ConnectionImpl c = socket.getUserData();
            socket.update((s, msg) -> Gdx.app.postRunnable(() -> {
                try{
                    net.handleServerReceived(c.id, msg);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }));
        }
    }

    public void send(Object object, SendMode mode){
        for(ConnectionImpl c : connections){
           sendRaw(c, object, mode);
        }
    }

    public void sendTo(int id, Object object, SendMode mode){
        sendRaw(getByID(id), object, mode);
    }

    public void sendExcept(int id, Object object, SendMode mode){
        for(ConnectionImpl c : connections){
            if(c.id != id){
                sendRaw(c, object, mode);
            }
        }
    }

    void sendRaw(ConnectionImpl c, Object object, SendMode mode){
        if(c == null ||  c.sock.getState() != SocketState.CONNECTED) return;

        if(object instanceof Streamable){
            c.sock.sendBig(object);
        }else if(mode == SendMode.reliable){
            c.sock.send(object);
        }else{
            c.sock.sendUnreliable(object);
        }
    }

    public void close(){
        if(socket != null) socket.close();
    }

    public Array<? extends NetConnection> getConnections(){
        array.clear();
        for(ConnectionImpl c : connections){
            array.add(c);
        }
        return array;
    }

    public ConnectionImpl getByID(int id){
        for(ConnectionImpl n : connections){
            if(n.id == id){
                return n;
            }
        }
        return null;
    }

    class ConnectionImpl extends NetConnection{
        private final Socket sock; //yes, it's a sock, absolutely hilarious

        public ConnectionImpl(Socket con){
            super(lastID++, con.getRemoteAddress().getHostAddress());
            this.sock = con;
        }

        @Override
        public void close(){
            sock.close();
        }
    }
}
