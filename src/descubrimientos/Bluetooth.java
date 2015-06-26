package descubrimientos;

import java.io.IOException;
import java.util.Scanner;
import java.util.Vector;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DataElement;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

/**
 * 
 * @author Manuel Mancera
 * @date 04/06/2014
 * 
 */
public class Bluetooth {
    // Prompt para la interfaz
    public static final String prompt = "Blue$ ";
    // Almacena los dispositivos
    public static final Vector<RemoteDevice> devices = new Vector<RemoteDevice>();

    // Usado para cuando buscamos un solo servicio no me este imprimiendo por pantalla
    // automaticamente el nombre de todos los que va encontrando
    public static int modo = 0;

    /**
     * @param args
     */
    public static void main(String[] args) {
	final Object inquiryCompletedEvent = new Object();
	final Object serviceSearchCompletedEvent = new Object();

	// Limpiamos la lista de dispositivo. Esta primera vez estará vacia de todos modos
	devices.clear();

	DiscoveryListener listener = new DiscoveryListener() {

	    public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
		devices.addElement(btDevice);
		if (modo == 0) {
		    System.out.println("[+] Device " + btDevice.getBluetoothAddress() + " found");
		    try {
			System.out.println("[+]\tName " + btDevice.getFriendlyName(false));
		    } catch (IOException cantGetDeviceName) {
		    }
		}
	    }

	    public void inquiryCompleted(int discType) {
		synchronized (inquiryCompletedEvent) {
		    inquiryCompletedEvent.notifyAll();
		}
	    }

	    public void serviceSearchCompleted(int transID, int respCode) {

		synchronized (serviceSearchCompletedEvent) {
		    serviceSearchCompletedEvent.notifyAll();
		}
	    }

	    public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
		for (int i = 0; i < servRecord.length; i++) {
		    DataElement serviceName = servRecord[i].getAttributeValue(0x0100);
		    if (serviceName != null) {
			System.out.println("[+] Service " + serviceName.getValue() + " found.");
		    } else {
			System.out.println("[+] Service found.");
		    }
		}
	    }
	};

	String action = "";
	Bluetooth blue = new Bluetooth();
	blue.helpMenu();
	try {
	    while (!action.equals("end")) {
		// Recibe la acción a ejecutar desde el menú
		action = blue.menu();
		if (action.equals("printInfo")) {
		    blue.printInfo();
		} else if (action.equals("findDevices")) {
		    // Limpia la lista de dispositivos, para empezar a buscar de
		    // nuevo
		    modo = 0;
		    devices.clear();
		    synchronized (inquiryCompletedEvent) {
			boolean started = LocalDevice.getLocalDevice().getDiscoveryAgent()
				.startInquiry(DiscoveryAgent.GIAC, listener);
			if (started) {
			    System.out.println("[*] Buscando dispositivos...");
			    inquiryCompletedEvent.wait();
			    System.out.println("[+] " + devices.size() + " device(s) found");
			}
		    }
		} else if (action.split("\\.")[0].equals("findDevice")) {
		    modo = 1; // Modo para que no imprima todos los
			      // dispositivos.
		    String name = action.split("\\.")[1];
		    System.out.println("[*] Searching " + name);
		    devices.clear();
		    synchronized (inquiryCompletedEvent) {
			boolean started = LocalDevice.getLocalDevice().getDiscoveryAgent()
				.startInquiry(DiscoveryAgent.GIAC, listener);
			if (started) {
			    inquiryCompletedEvent.wait();
			}
		    }
		    // Ahora buscamos el dispositivo entre los que ha encontrado
		    boolean found = false;
		    for (int i = 0; i < devices.size(); i++) {
			if ((devices.get(i).getBluetoothAddress().equals(name))
				|| devices.get(i).getFriendlyName(true).equals(name)) {
			    System.out.println("[+] Device found!\n\t[+] Addr: "
				    + devices.get(i).getBluetoothAddress()
				    + "\n\t[+] Friendly name: "
				    + devices.get(i).getFriendlyName(false));
			    found = true;
			}
		    }
		    if (!found) {
			System.out.println("[-] Device not found.");
		    }
		} else if (action.equals("findServices")) {
		    // Para buscar servicios primero necesitamos buscar que dispositivos hay, por
		    // tanto se deberia ejecutar la opcion 1 antes de esta opcion
		    if (devices.size() > 0) {
			UUID[] uuids = new UUID[1];
			uuids[0] = new UUID(0x1002); // 0x1002 es el Public Browser Group
						     // Universally Unique Identifier.
						     // Y se engarga de enumerar los servicios que
						     // esta publicando el dispositivos.
			int[] attrIDs = new int[] { 0x0100 // Recuperar el nombre del servicio.
			};

			for (RemoteDevice device : devices) {
			    synchronized (serviceSearchCompletedEvent) {
				System.out.println("[*] Buscando servicios: "
					+ device.getBluetoothAddress() + " "
					+ device.getFriendlyName(false));
				LocalDevice.getLocalDevice().getDiscoveryAgent()
					.searchServices(attrIDs, uuids, device, listener);
				serviceSearchCompletedEvent.wait();
			    }
			}
		    } else {
			System.out.println("[-] Tienes que buscar dispositivos primero.");
		    }
		} else if (action.split("\\.")[0].equals("findService")) {
		    // Primero debe buscarlo y luego mirar sus servicios
		    // Si ya está en la lista de escaneados se puede buscar
		    // servicios directamente
		    RemoteDevice deviceService = null;
		    for (RemoteDevice device : devices) {
			if (device.getBluetoothAddress().equals(action.split("\\.")[1])
				|| device.getFriendlyName(false).equals(action.split("\\.")[1])) {
			    deviceService = device;
			}
		    }
		    if (deviceService != null) {
			UUID[] uuids = new UUID[1];
			uuids[0] = new UUID(0x1002);
			int[] attrIDs = new int[] { 0x0100 // Service name
			};
			synchronized (serviceSearchCompletedEvent) {
			    System.out.println("[*] Buscando servicios: "
				    + deviceService.getBluetoothAddress() + " "
				    + deviceService.getFriendlyName(false));
			    LocalDevice.getLocalDevice().getDiscoveryAgent()
				    .searchServices(attrIDs, uuids, deviceService, listener);
			    serviceSearchCompletedEvent.wait();
			}
		    } else {
			System.out.println("[-] Dispositivo no encontrado. Intentalo de nuevo.");
		    }
		} else if (action.equals("help")) {
		    blue.helpMenu();
		} else if (!action.equals("end")) {
		    // Se ha escogido una opción no válida.
		    System.out.println(action);
		    blue.helpMenu(); // Imprime el menú
		}
	    }
	} catch (BluetoothStateException e) {
	    System.out
		    .println("[-] Dispositivo no disponible. Activa el dispositivo y vuelve a ejecutar el programa.");
	} catch (IOException e) {
	    e.printStackTrace();
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}
    }

    /**
     * Imprime menú
     */
    public void helpMenu() {
	System.out.println(""
		+ "*****************************************************************\n"
		+ "*\t\t\t BlueTooth Client  \t\t\t*\n" + "*\t 1) Informacion \t\t\t\t\t*\n"
		+ "*\t 2) Descubrir dispositivos\t\t\t\t*\n"
		+ "*\t 3) Descubre un dispositivo\t\t\t\t*\n"
		+ "*\t 4) Escanear los servicios de todos los dispositivos \t*\n"
		+ "*\t 5) Escanear servicios de un dispositivo \t\t*\n"
		+ "*\t 6) Muestra el menu \t\t\t\t\t*\n" + "*\t 7) Salir \t\t\t\t\t\t*\n"
		+ "*****************************************************************");
    }

    /**
     * Imprime por pantalla información del dispositivo bluetooth
     */
    @SuppressWarnings("static-access")
    public void printInfo() {
	LocalDevice local = null;
	try {
	    local = LocalDevice.getLocalDevice();
	    System.out
		    .println("[*] Device: "
			    + local.getBluetoothAddress()
			    + "\n[*] Name: "
			    + local.getFriendlyName()
			    + "\n[*] Property:"
			    + "\n\t[*] BlueTooth Java API: "
			    + local.getProperty("bluetooth.api.version")
			    + "\n\t[*] Master: "
			    + local.getProperty("bluetooth.master.switch")
			    + "\n\t[*] Maximum number of service attributes to be retrieved per service record: "
			    + local.getProperty("bluetooth.sd.attr.retrievable.max")
			    + "\n\t[*] Maximum number of connected devices supported: "
			    + local.getProperty("bluetooth.connected.devices.max")
			    + "\n\t[*] Maximum ReceiveMTU size in bytes supported in L2CAP: "
			    + local.getProperty("bluetooth.l2cap.receiveMTU.max")
			    + "\n\t[*] Maximum number of concurrent service discovery transactions: "
			    + local.getProperty("bluetooth.sd.trans.max"));

	} catch (BluetoothStateException e) {
	    System.out.println("[-] Dispositivo no disponible.");
	}
    }

    /**
     * Menú para interactuar con el usuario desde el terminal. :) Tiene 5 opciones, 1 Información, 2 Buscar
     * dispositivos, 3 Busca dispositivo, 4 Buscar servicios, 5 busca servicios, 6 Salir
     * 
     * @throws IOException
     * @throws InterruptedException
     */
    public String menu() throws IOException, InterruptedException {
	Scanner input = new Scanner(System.in);
	String command;
	System.out.print(prompt + " ");
	command = input.nextLine();
	if (command.split(" ")[0].toUpperCase().equals("7")) {
	    input.close();
	    return "end";
	} else if (command.split(" ")[0].toUpperCase().equals("1")) {
	    return "printInfo";
	} else if (command.split(" ")[0].toUpperCase().equals("2")) {
	    return "findDevices";
	} else if (command.split(" ")[0].toUpperCase().equals("3")) {
	    System.out.print("Introduce nombre/addr del dispositivo: ");
	    String name = input.nextLine();
	    return "findDevice." + name;
	} else if (command.split(" ")[0].toUpperCase().equals("4")) {
	    return "findServices";
	} else if (command.split(" ")[0].toUpperCase().equals("5")) {
	    System.out.print("Introduce nombre/addr del dispositivo: ");
	    String name = input.nextLine();
	    return "findService." + name;
	} else if (command.split(" ")[0].toUpperCase().equals("6")) {
	    return "help";
	} else {
	    return "[-] Opción no válida.";
	}
    }

}
