import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Server extends Task<Void> {

    private final ChatController chatController;
    private final ServerSocket serverSocket;
    private final ExecutorService threadPool;

    public Server(ChatController chatController, ServerSocket serverSocket) {
        this.chatController = chatController;
        this.serverSocket = serverSocket;
        this.threadPool = Executors.newFixedThreadPool(5);
    }


    @Override
    protected Void call() throws Exception {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                chatController.tryConnection(socket);

            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
        return null;
    }

    private void trySocket(Socket socket) {
        if (chatController.hasOngoingSession()) {
            threadPool.submit(new SendDeclinedAndCloseTask(socket));
        } else {

            // prompt user for confirmation
            String inetAddress = socket.getInetAddress().toString();
            var confirm = new GetConfirmationFromUserTask(inetAddress);
            Platform.runLater(confirm);

            boolean accepted;
            try {
                accepted = confirm.get();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            if (!accepted) {
                threadPool.submit(new SendDeclinedAndCloseTask(socket));
            } else {
                SendAcceptedTask sendAcceptedTask = new SendAcceptedTask(socket);
                sendAcceptedTask.setOnSucceeded((workerStateEvent) -> {
                    chatController.receiveSocket(socket);
                });
                threadPool.submit(sendAcceptedTask);
            }

        }


    }


}
