package com.comino.mavutils.workqueue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.comino.mavutils.legacy.ExecutorService.DaemonThreadFactory;

public class WorkQueue { 

	private static WorkQueue instance;
	private final long ns_ms = 1000000L;

	private final Map<String,Worker> queues = new ConcurrentHashMap<String,Worker>();

	public static WorkQueue getInstance() {
		if(instance == null)
			instance = new WorkQueue();
		return instance;
	}

	private WorkQueue() {

		queues.put("LP",  new Worker("LP",Thread.MIN_PRIORITY));
		queues.put("NP",  new Worker("NP",Thread.NORM_PRIORITY));
		queues.put("HP",  new Worker("HP",Thread.MAX_PRIORITY));

	}

	public int addCyclicTask(String queue, int cycle_ms, Runnable runnable) {
		return queues.get(queue).add(new WorkItem(runnable.getClass().getCanonicalName(),runnable , cycle_ms, false));
	}

	public int addSingleTask(String queue, int delay_ms, Runnable runnable) {
		return queues.get(queue).add(new WorkItem(runnable.getClass().getCanonicalName(),runnable ,delay_ms, true));
	}

	public void removeTask(String queue, int id) {
		//queues.get(queue).remove(id);
	}

	public void start() {
		queues.forEach((i,w) -> {
			w.start();
		});
	}

	public void stop() {
		queues.forEach((i,w) -> {
			w.stop();
		});
	}

	public void printStatus() {
		queues.forEach((i,w) -> {
			if(!w.queue.isEmpty()) {
				System.out.println("Queue "+i+" (Overruns: "+w.getExceeded()+"):");
				w.print(); 
			}
		});
	}

	private class Worker implements Runnable {

		private final Map<Integer, WorkItem>     queue = new ConcurrentHashMap<Integer, WorkItem>();
		private final ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);

		private String     name         = null;
		private long       min_cycle_ns = 1000000000;
		private long       tms          = 0;
		private int        exceeded     = 0;
		private boolean    isRunning    = false;

		public Worker(String name, int priority) {
			this.name = name;
			pool.setThreadFactory(new DaemonThreadFactory(priority,"WQ_"+name));
			pool.allowCoreThreadTimeOut(false);
			pool.setRemoveOnCancelPolicy(true);
			pool.prestartAllCoreThreads();	

		}

		public int add(WorkItem item) {
			int id = item.hashCode();
			if(!item.once && min_cycle_ns  > item.cycle_ns && item.cycle_ns > 0) {
				min_cycle_ns = item.cycle_ns;
			}
			queue.put(id,item);	
			return id;
		}

		public int getExceeded() {
			return exceeded;
		}

		public void start() {

			if(isRunning)
				return;

			isRunning = true;
			System.out.println("WorkQueue "+name+" started ("+min_cycle_ns/ns_ms+"ms)");
			pool.submit(this);
		}

		public void stop() {
			isRunning = false;
			queue.clear();
			pool.shutdown();
		}

		public void print() {
			queue.forEach((i,w) -> {
				System.out.println(" ->  "+w);
			});
		}

		@Override
		public void run() {
			long exec_cycle;
			while(isRunning) {

				tms = System.nanoTime();
				queue.forEach((i,k) -> {
					k.run();
					if(k.once && k.count > 0) 
						queue.remove(i);
				});

				exec_cycle = System.nanoTime()  - tms;

				if(exec_cycle > min_cycle_ns) 
					exceeded++;

				LockSupport.parkNanos(min_cycle_ns/10);

			}

		}
	}

	private class WorkItem {

		private String             name;
		private Runnable       runnable;
		private long           cycle_ns;
		private long          last_exec;
		private long           act_exec;
		private long          act_cycle;
		private int               count;
		private boolean            once;

		public WorkItem(String name, Runnable runnable, int cycle_ms, boolean once) {
			this.name           = name;
			this.runnable       = runnable;
			this.cycle_ns       = (long)cycle_ms * ns_ms;
			this.act_cycle      = 0;
			this.count          = 0;
			this.once           = once;

			if(once)
				this.last_exec      = System.nanoTime();
			else
				this.last_exec      = 0;
		}

		public String toString() {
			if(once)
				return (cycle_ns/ns_ms)+"ms\t"+act_exec +"us\t"+name;
			if(act_cycle>0)
				return String.format("%3.1f",1000f/act_cycle)+"Hz\t"+act_exec +"us\t"+name;
			return "\t"+name;
		}

		public void run() {
			try { 
				if((System.nanoTime() - last_exec) >= cycle_ns) {
					count++;
					if(last_exec > 0)
						act_cycle =  ( System.nanoTime() - last_exec) / ns_ms  ;
					last_exec = System.nanoTime();
					runnable.run();
					act_exec  = ( System.nanoTime() - last_exec) / 1000 ;
				}
			} catch( Exception e ) {e.printStackTrace(); }
		}
	}


	public static void main(String[] args) {

		WorkQueue q = WorkQueue.getInstance();

		final long tms = System.currentTimeMillis();

		q.addCyclicTask("LP", 50,  () ->  { try { Thread.sleep(10); } catch (InterruptedException e) {} });
		q.addCyclicTask("LP", 50,  () ->  { try { Thread.sleep(2);  } catch (InterruptedException e) {} });
		q.addCyclicTask("LP", 100, () ->  { try { Thread.sleep(20); } catch (InterruptedException e) {} });
		q.addCyclicTask("NP", 200, () ->  { try { Thread.sleep(2);  } catch (InterruptedException e) {} });
		q.addCyclicTask("HP", 500, () ->  { try { Thread.sleep(5);  } catch (InterruptedException e) {} });
		q.addSingleTask("LP", 1000, () -> {  System.out.println("1: "+(System.currentTimeMillis()-tms)); });
		q.addSingleTask("LP", 3000, () -> { 
			try { System.out.println("2: "+(System.currentTimeMillis()-tms)) ;
			q.addSingleTask("LP", 4000, () -> {  System.out.println("3: "+(System.currentTimeMillis()-tms)); });
			} catch( Exception e ) {e.printStackTrace(); }
		});
		q.addCyclicTask("HP", 10,  () ->  { try { Thread.sleep(2);  } catch (InterruptedException e) {} });

		q.start();

		int count = 0;
		while(count++ < 30) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) { }

			q.printStatus();
		}

		q.stop();


	}

}
