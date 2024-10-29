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

	// Drivers are placed in idle driver queue 
    private final ConcurrentLinkedQueue<Driver> driverQueue;

	// Save region info
    private final HashMap<String, Integer> regionInfo;
    private final HashMap<String, NuberRegion> regionInfos;

    // synchronised Incrementing and Decrementing Booking Counts, thread-safe because using AtomicInteger
    private final AtomicInteger bookingsAwaitingDriver; 

	// synchronous lock
	private final ReentrantLock shutdownLock = new ReentrantLock();

    public NuberDispatch(HashMap<String, Integer> regionInfo, boolean logEvents) {
        this.logEvents = logEvents;
        this.regionInfo = regionInfo;
        this.driverQueue = new ConcurrentLinkedQueue<>();
        this.bookingsAwaitingDriver = new AtomicInteger(0);
        this.regionInfos = new HashMap<>();
		System.out.println("Creating Nuber Dispatch");
		
		// create and save nuber Region in Hash Map
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
		//Adds drivers to a queue of idle driver.
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

	/**
	 * Books a given passenger into a given Nuber region.
	 * 
	 * Once a passenger is booked, the getBookingsAwaitingDriver() should be returning one higher.
	 * 
	 * If the region has been asked to shutdown, the booking should be rejected, and null returned.
	 * 
	 * @param passenger The passenger to book
	 * @param region The region to book them into
	 * @return returns a Future<BookingResult> object
	 */
    public Future<BookingResult> bookPassenger(Passenger passenger, String region) {
    
		// Get the region from the map of regions
		NuberRegion nuberRegion = regionInfos.get(region);
	
		// Check if the region does not exist or has been shut down
		if (nuberRegion == null || nuberRegion.isShutdown()) {
			logEvent(null, "Rejected booking: Region " + region + " is shut down or does not exist.");
			return null;  // Return null as no booking can be made
		}
	
		// Check if the region has reached its maximum booking capacity
		if (!nuberRegion.canAcceptBooking()) {
			logEvent(null, "Rejected booking: Region " + region + " has reached its maximum capacity.");
			return CompletableFuture.completedFuture(null);  // Return a completed Future with null
		}
	
		// Increment the count of bookings waiting for a driver
		bookingsAwaitingDriver.incrementAndGet();
		// Increase the active booking count in the region
		nuberRegion.incrementBookingCount();
	
		// Process the booking asynchronously
		return CompletableFuture.supplyAsync(() -> {
			try {
				// Create a new booking with the dispatch and passenger details
				Booking booking = new Booking(this, passenger);
				logEvent(booking, "Creating booking"); // Log booking creation
				logEvent(booking, "Start booking, getting driver"); // Log that booking has started
	
				// Call the booking process, which handles driver assignment and trip completion
				BookingResult result = booking.call();
	
				// Check if the booking result is null (indicating failure or interruption)
				if (result == null) {
					// Decrement the count of bookings awaiting a driver
					bookingsAwaitingDriver.decrementAndGet();
					// Decrement the active booking count in the region
					nuberRegion.decrementBookingCount();
					return null; // Return null if booking result is unavailable
				}
				// Return the booking result upon successful completion
				return result;
				
			} catch (Exception e) {
				// In case of an error, decrement the counts to ensure correct tracking
				bookingsAwaitingDriver.decrementAndGet();
				nuberRegion.decrementBookingCount();
				System.out.println("Error in booking passenger: " + e.getMessage()); // Log error
				return null;  // Return null if an error occurs
			}
		});
	}
	

	//Dispatch can accurately report on the number of bookings awaiting a driver
    public int getBookingsAwaitingDriver() {
        return bookingsAwaitingDriver.get();
    }

	// decrease number of booking awaiting driver
    public void decrementBookingsAwaitingDriver() {
        bookingsAwaitingDriver.decrementAndGet();
    }

	// all region shutdown
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
