package com.comino.mavutils.hw;

import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

import com.comino.mavutils.hw.jetson.CPUTemperature;
import com.comino.mavutils.linux.LinuxUtils;

public class HardwareAbstraction {

	public static final int UPBOARD = 0;
	public static final int JETSON  = 1;
	public static final int SITL    = 2;

	private static HardwareAbstraction instance = null;

	private OperatingSystemMXBean osBean = null;
	private MemoryMXBean mxBean = null;

	private ICPUTemperature temp;
	private WifiQuality     wifi;

	private String arch;
	private int    archid;
	private int    cpu;

	public static HardwareAbstraction instance() {
		if(instance==null)
			instance = new HardwareAbstraction();
		return instance;
	}

	private HardwareAbstraction() {

		mxBean = java.lang.management.ManagementFactory.getMemoryMXBean();
		osBean =  java.lang.management.ManagementFactory.getOperatingSystemMXBean();
		arch = osBean.getArch();

		if(arch.contains("aarch64")) {
			archid = JETSON;
			temp = new com.comino.mavutils.hw.jetson.CPUTemperature();
			System.out.println("Jetson Nano architecture found..");
			wifi = new WifiQuality("wlan0");
		}
		else if (arch.contains("x86_64")){
			archid = SITL;
			temp = new com.comino.mavutils.hw.upboard.CPUTemperature();
			System.out.println("Intel SITL architecture found..");
			wifi = new WifiQuality(null);
		}
		else if (arch.contains("amd64")){
			archid = UPBOARD;
			temp = new com.comino.mavutils.hw.upboard.CPUTemperature();
			System.out.println("Intel UpBoard architecture found..");
			wifi = new WifiQuality("wlx74da38805d92");
		}

		mxBean = java.lang.management.ManagementFactory.getMemoryMXBean();


	}

	public int getArchId() {
		return archid;
	}

	public int getTemperature() {
      return temp.get();
	}

	public byte getWifiQuality() {
		return (byte)wifi.get();
	}

	public String getArchName() {
		return arch;
	}

	public int getMemoryUsage() {
		return (int)(mxBean.getHeapMemoryUsage().getUsed() * 100 /mxBean.getHeapMemoryUsage().getMax());
	}

	public int getCPULoad() {
     return cpu;
	}

	public void update() {

		temp.determine();
		wifi.getQuality();

		try {
			cpu = LinuxUtils.getProcessCpuLoad();
		} catch (Exception e) {	}
	}



}
