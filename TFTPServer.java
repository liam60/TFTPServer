import java.net.*;
import java.io.*;
import java.util.*;

class TftpServerWorker extends Thread
{
    private DatagramPacket _dataPacket;
    private DatagramPacket _prevPacket;
    private DatagramSocket _socket;
    private InetAddress _address;
    private int _port;
    private boolean _flag;
    private static final byte RRQ = 1;
    private static final byte DATA = 2;
    private static final byte ACK = 3;
    private static final byte ERROR = 4;
    private int t;



	/*####################################################################################################################################*/

	//Sends a file to the requestor given a file name
    private void sendfile(String filename)
    {
    	try{
			File file = new File(filename);
			FileInputStream fis = new FileInputStream(file);
			
			byte[] flag = new byte[2];
			byte[] contents = new byte[512];
			byte[] packet = new byte[514];
			int r = 0;
			byte blockNumber = 1;
			_flag = true;
			while((r = fis.read(contents)) != -1)
			{
				flag[0] = DATA;
		    	flag[1] = blockNumber;
				blockNumber = (byte)(blockNumber + 1);

				//Send 512b of file
		    	System.arraycopy(flag, 0, packet, 0, 2);
		    	System.arraycopy(contents, 0, packet, 2, contents.length);
		    	_prevPacket = new DatagramPacket(packet, r+2, _address, _port);
		    	t = 0;
				transmit();
				if(t==5){
					return;
				}
				
		    	if(r != 512)
				{
					_flag = false;
					System.out.println("End File");
					break;
				}
				
			}
			if(_flag == true)
			{
				//Signal EOF
				flag[0] = DATA;
		    	flag[1] = blockNumber;
		    	System.arraycopy(flag, 0, packet, 0, 2);
		    	contents = new byte[1];
		    	System.arraycopy(contents, 0, packet, 2, 0);
		    	_prevPacket = new DatagramPacket(packet, 0+2, _address, _port);
		    	t = 0;
				transmit();
				if(t==5){
					return;
				}
			}
			
		}
		catch(Exception ex)
		{
			System.err.println(ex.getMessage());
		}
		return;
    }


	public void transmit()
	{
		try
		{
			t++;
			if(t==5)
			{
				return;
			}
			_socket.send(_prevPacket);	
		    	
			//wait for ACK
			byte[] buf = new byte[514];
	   		DatagramPacket ackPacket = new DatagramPacket(buf, 514);

	   		_socket.receive(ackPacket);
	   		return;
	   	}
	    catch(SocketTimeoutException se)
		{
			System.err.println("Ack timeout");
			transmit();
		}
		catch(Exception e)
		{
			System.err.println(e);
		}
	}

	/*####################################################################################################################################*/
    public void run()
    {
	    try{
 		    if(_dataPacket.getData()[0] == RRQ)
		    {
				String str = new String(_dataPacket.getData(),1 , _dataPacket.getLength()-1);
				File file = new File(str);
			    if(!file.exists())
			    {
					sendError("File not found on the server.");
					return;
			    }
		    	sendfile(str);
		    }
		    return;
	    }
	    catch(Exception e)
	    {
			System.err.println(e);
	    }
    }
    
    
    /*####################################################################################################################################*/

	public void sendError(String c)
	{
        try {
		    
    		byte[] error = new byte[1];
    		error[0] = ERROR;
            byte[] errorMessage = c.getBytes();
            byte[] errorData = new byte[errorMessage.length + 1];
            
		    System.arraycopy(error, 0, errorData, 0, 1);
		    System.arraycopy(errorMessage, 0, errorData, 1, errorMessage.length);
		    
		    DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length, _address, _port);
		    _socket.send(errorPacket);
        }
        catch(Exception e)
        {
			System.err.println(e.getMessage());
        }	
    }
    
    /*####################################################################################################################################*/

    public TftpServerWorker(DatagramPacket req)
    {
		_dataPacket = req;
		try{
			_socket = new DatagramSocket();
			_socket.setSoTimeout(1000);
	    	_address = _dataPacket.getAddress();
	    	_port = _dataPacket.getPort();
		}
		catch(Exception e)
		{
			System.err.println(e.getMessage());
		}    
	}
}




/*####################################################################################################################################*/
/*####################################################################################################################################*/
/*####################################################################################################################################*/




class TftpServer
{
    public void start_server()
    {
		try {
	    	DatagramSocket ds = new DatagramSocket();
	   		System.out.println("TftpServer on port " + ds.getLocalPort());
	
			while(1==1)
			{
				byte[] buf = new byte[514];
				DatagramPacket p = new DatagramPacket(buf, 514);
				ds.receive(p);
				TftpServerWorker worker = new TftpServerWorker(p);
				worker.start();
		    }
		}
		catch(Exception ex){
			System.err.println(ex);
		}

		return;
    }

	/*####################################################################################################################################*/

    public static void main(String args[])
    {
		TftpServer d = new TftpServer();
		d.start_server();
    }
}





/*####################################################################################################################################*/
/*####################################################################################################################################*/
/*####################################################################################################################################*/





class TftpClient
{
    private static final byte RRQ = 1;
    private static final byte DATA = 2;
    private static final byte ACK = 3;
    private static final byte ERROR = 4;
	private static int port;
	private static InetAddress address;
	private static DatagramSocket socket = null;
	private static DatagramPacket packet;
	private static byte[] sendBuf = new byte[512];

	/*####################################################################################################################################*/

	public static void main(String args[])
	{
		try {
			//Checks if the correct number of arguments is supplied
			if(args.length != 3)
			{
				System.out.println("Usage: java TftpClient <hostname> <portnumber> <filename>");
				return;
			}
			
			
			//Sends a request to the Server
			int port = Integer.valueOf(args[1]);
			socket = new DatagramSocket();
			byte[] flag = new byte[1];
			flag[0] = RRQ;
			byte[] filename = args[2].getBytes();
			byte[] request = new byte[flag.length + filename.length];
			System.arraycopy(flag, 0, request, 0, 1);
			System.arraycopy(filename, 0, request, 1, filename.length);
			InetAddress ad = InetAddress.getByName(args[0]);
			DatagramPacket packet = new DatagramPacket(request, request.length, ad, port);
			socket.send(packet);
			
			
			
			//Creates a new fileoutputstream writing to the a file 
			//with the name of the file requested			
			FileOutputStream fos = new FileOutputStream("CopyOf"+args[2]);
			byte blockNumber = 1;
			byte[] buf = new byte[514];
            byte[] block = new byte [2];
			byte[] contents = new byte[512];
			//boolean drop = true;
			
            while(1==1)
            {
				//Reads in the 512 bytes sent
                DatagramPacket p = new DatagramPacket(buf, 514);
				socket.receive(p);
                buf = p.getData();
                block = Arrays.copyOfRange(buf, 0, 2);
                contents = Arrays.copyOfRange(buf, 2, p.getLength());
                
                if(buf[0] == ERROR)
                {
                    String str = new String(buf, 1, buf.length-1);
                    System.out.println("Error Received: " + str);
                    break;
                }
                if(buf[0] == DATA)
                {
                	//if(buf[1] == 5 && drop == true)
                	//{
						//drop = false;
					//}
                	if(block[1] == blockNumber || block[1] == (blockNumber-1))
                	{
                		blockNumber = (byte)(block[1] + 1);
                		if(p.getLength() < 514)
                    	{
                    		fos.write(contents, 0, contents.length);
                    	}
                    	else
                    	{
                			fos.write(contents);
                    	}
                    
                    	//Send an ACK
                    	byte[] acknowledge = new byte[2];
                    	acknowledge[0] = ACK;
                    	acknowledge[1] = blockNumber;
                    	DatagramPacket ac = new DatagramPacket(acknowledge, acknowledge.length, p.getAddress(), p.getPort());
                    	socket.send(ac);
                    	if(p.getLength() < 514) break;
                    }
                    else
                    {
                    	break;
                    }				
                }
            }
            
            fos.close();

		}
		catch(Exception e){
			System.err.println("Error: " + e.getMessage());
		}
	}
}
