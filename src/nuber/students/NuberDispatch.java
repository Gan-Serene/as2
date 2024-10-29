package nuber.students;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class NuberDispatch {

    private final int MAX_DRIVERS = 999;
    private final boolean logEvents;

    private final ConcurrentLinkedQueue<Driver> driverQueue;
    private final HashMap<String, Integer> regionInfo;
    private final HashMap<String, NuberRegion> regionInfos;
    private final AtomicInteger bookingsAwaitingDriver;
    private final ReentrantLock shutdownLock = new ReentrantLock();

    public NuberDispatch(HashMap<String, Integer> regionInfo, boolean logEvents) {
        this.logEvents = logEvents;
        this.regionInfo = regionInfo;
        this.driverQueue = new ConcurrentLinkedQueue<>();
        this.bookingsAwaitingDriver = new AtomicInteger(0);
        this.regionInfos = new HashMap<>();

        System.out.println("Creating Nuber Dispatch");
        for (Map.Entry<String, Integer> entry : regionInfo.entrySet()) {
            String regionName = entry.getKey();
            int maxSimultaneousJobs = entry.getValue();
            regionInfos.put(regionName, new NuberRegion(this, regionName, maxSimultaneousJobs));
            System.out.println("Creating Nuber region for " + regionName);
        }
        System.out.println("Done creating " + regionInfos.size() + " regions");
    }

    /**
	 * Adds drivers to a queue of idle driver.
	 *  
	 * Must be able to have drivers added from multiple threads.
	 * 
	 * @param The driver to add to the queue.
	 * @return Returns true if driver was added to the queue
	 */
	public boolean addDriver(Driver newDriver)
	{
		if (driverQueue.size() >= MAX_DRIVERS) {
			System.out.println("Queue is full");
            return false; // Queue is full
        }
        driverQueue.add(newDriver);
        return true;
	}
	
	/**
	 * Gets a driver from the front of the queue
	 *  
	 * Must be able to have drivers added from multiple threads.
	 * 
	 * @return A driver that has been removed from the queue
	 */
	public Driver getDriver(){
		return driverQueue.poll();
	}

	/**
	 * Prints out the string
	 * 	    booking + ": " + message
	 * to the standard output only if the logEvents variable passed into the constructor was true
	 * 
	 * @param booking The booking that's responsible for the event occurring
	 * @param message The message to show
	 */
	public void logEvent(Booking booking, String message) {
		if (!logEvents) return;
		System.out.println(booking + ": " + message);
	}

    public Future<BookingResult> bookPassenger(Passenger passenger, String region) {
        NuberRegion nuberRegion = regionInfos.get(region);

        if (nuberRegion == null || nuberRegion.isShutdown()) {
            logEvent(null, "Rejected booking: Region " + region + " is shut down or does not exist.");
            return null;
        }

        // Simultaneous live bookings are limited to the maximum allowed for a given region
        if (!nuberRegion.canAcceptBooking()) {
            logEvent(null, "Rejected booking: Region " + region + " has reached its maximum capacity.");
            
			return CompletableFuture.completedFuture(null);
        }

        bookingsAwaitingDriver.incrementAndGet();
        nuberRegion.incrementBookingCount();

        return CompletableFuture.supplyAsync(() -> {
            try {
                Booking booking = new Booking(this, passenger);
                logEvent(booking, "Creating booking");
                logEvent(booking, "Start booking, getting driver");

                BookingResult result = booking.call();

                if (result == null) {
                    bookingsAwaitingDriver.decrementAndGet();
                    nuberRegion.decrementBookingCount();
                    return null;
                }
                return result;
            } catch (Exception e) {
                bookingsAwaitingDriver.decrementAndGet();
                nuberRegion.decrementBookingCount();
                System.out.println("Error in booking passenger: " + e.getMessage());
                return null;
            }
        });
    }

	//Dispatch can accurately report on the number of bookings awaiting a driver
    public int getBookingsAwaitingDriver() {
        return bookingsAwaitingDriver.get();
    }

    public void decrementBookingsAwaitingDriver() {
        bookingsAwaitingDriver.decrementAndGet();
    }

    public void shutdown() {
        shutdownLock.lock();
        try {
            for (NuberRegion region : regionInfos.values()) {
                region.shutdown();
            }
            logEvent(null, "NuberDispatch system is shutting down");
        } finally {
            shutdownLock.unlock();
        }
    }
}
