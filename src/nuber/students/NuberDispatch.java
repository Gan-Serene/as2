package nuber.students;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The core Dispatch class that instantiates and manages everything for Nuber
 * 
 * @author james
 *
 */
public class NuberDispatch {

	/**
	 * The maximum number of idle drivers that can be awaiting a booking 
	 */
	private final int MAX_DRIVERS = 999;
	
	private boolean logEvents = false;

	// Thread-safe queue to store idle drivers
    private ConcurrentLinkedQueue<Driver> driverQueue = new ConcurrentLinkedQueue<>();
    
    // Map of region names and max simultaneous bookings they can handle
    private HashMap<String, Integer> regionInfo;
	private HashMap<String, NuberRegion> regionInfos;
    
    // Counter for bookings awaiting drivers
    private AtomicInteger bookingsAwaitingDriver = new AtomicInteger(0);

    // Lock for shutting down regions safely
    private ReentrantLock shutdownLock = new ReentrantLock();
    
    // Track if the system is in shutdown mode
    private volatile boolean shutdown = false;
	
	/**
	 * Creates a new dispatch objects and instantiates the required regions and any other objects required.
	 * It should be able to handle a variable number of regions based on the HashMap provided.
	 * 
	 * @param regionInfo Map of region names and the max simultaneous bookings they can handle
	 * @param logEvents Whether logEvent should print out events passed to it
	 */
	public NuberDispatch(HashMap<String, Integer> regionInfo, boolean logEvents)
	{
		this.logEvents = logEvents;
        this.regionInfo = new HashMap<>();
        this.driverQueue = new ConcurrentLinkedQueue<>();
        this.bookingsAwaitingDriver = new AtomicInteger(0);
		this.regionInfos = new HashMap<String, NuberRegion>();
        for (Map.Entry<String, Integer> entry : regionInfo.entrySet()) {
            String regionName = entry.getKey();
            int maxSimultaneousJobs = entry.getValue();
            this.regionInfos.put(regionName, new NuberRegion(this, regionName, maxSimultaneousJobs));
		}
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
		if (shutdown) {
			return null; // Return null if the system is shutting down
		}
	
		// Increment the counter for bookings awaiting a driver
		bookingsAwaitingDriver.incrementAndGet();

		Integer nuberRegion = regionInfo.get(region);
        if (nuberRegion == null) {
            bookingsAwaitingDriver.decrementAndGet(); // Decrement if the region is invalid
            return CompletableFuture.completedFuture(null); // Return null if the region is not found
        }
	
		// Process the booking asynchronously
		return CompletableFuture.supplyAsync(() -> {
			try {
				// Create a new booking instance with the dispatch and passenger
				Booking booking = new Booking(this, passenger);
				
				// Call the booking process, which handles driver allocation and trip completion
				BookingResult result = booking.call(); 
	
				if (result == null) {
					// If no result is returned, decrement the counter
					bookingsAwaitingDriver.decrementAndGet();
					return null; // No result indicates no available driver or interrupted process
				}
	
				// Decrement awaiting bookings counter after successful processing
				bookingsAwaitingDriver.decrementAndGet();
	
				// Return the booking result
				return result;
			} catch (Exception e) {
				// In case of an error, ensure the awaiting bookings counter is decremented
				bookingsAwaitingDriver.decrementAndGet();
				System.out.println("book passenger Error");
				return null; // Return null if an error occurs
			}
		});
    }

	/**
	 * Gets the number of non-completed bookings that are awaiting a driver from dispatch
	 * 
	 * Once a driver is given to a booking, the value in this counter should be reduced by one
	 * 
	 * @return Number of bookings awaiting driver, across ALL regions
	 */
	public int getBookingsAwaitingDriver()
	{	return bookingsAwaitingDriver.get();
	}
	
	/**
	 * Tells all regions to finish existing bookings already allocated, and stop accepting new bookings
	 */
	public void shutdown() {
		shutdownLock.lock();
        try {
            shutdown = true;
			for (Map.Entry<String, Integer> entry : regionInfo.entrySet()) {
				String regionName = entry.getKey();
            	NuberRegion region = regionInfos.get(regionName);
            region.shutdown();
		}
        } finally {
            shutdownLock.unlock();
        }
	}

}
