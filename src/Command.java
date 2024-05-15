import java.io.Serializable;

/*
Protocol for conversation between localhost and remote host:

0. Stream socket is opened/accepted                     -> 1

1a. localhost is the initiator                          -> 2
1b. remote host is the initiator                        -> 6

2a. localhost receives ACCEPTED from remote host        -> 3
2b. localhost receives DECLINED from remote host        -> 10

3. localhost sends public key to remote host            -> 4

4. localhost receives remote host's public key          -> 5

5. The two clients can now communicate by sending MESSAGE, followed by the encrypted and signed message

6a. There is already an ongoing session                 -> 8
6b. There is no ongoing session                         -> 7

7 The user at localhost is prompted for accept/decline
a. user declines                                        -> 8
b. user accepts                                         -> 9

8. localhost sends DECLINED                             -> 10

9. localhost sends ACCEPTED                             -> 3

10. connection is terminated, socket closed


if at any time localhost cannot parse message from remote host or if remote host closes the socket
                                                        -> 10

 */
public enum Command implements Serializable {
    ACCEPTED, DECLINED, MESSAGE
}
