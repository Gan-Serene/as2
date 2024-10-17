package nuber.students;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single Nuber region that operates independently of other regions, other than getting 
 * drivers from bookings from the central dispatch.
 * 
 * A region has a maxSimultaneousJobs setting that defines the maximum number of bookings 
 * that can be active with a driver at any time. For passengers booked that exceed that 
 * active count, the booking is accepted, but must wait until a position is available, and 
 * a driver is available.
 * 
 * Bookings do NOT have to be completed in FIFO order.
 * 
 * @author james
 *
 */
public class NuberRegion {

	private final NuberDispatch dispatch;
    private final String regionName;
    private final int maxSimultaneousJobs;
    private final ConcurrentLinkedQueue<Passenger> waitingPassengers;
    private final AtomicInteger activeBookings;
    private boolean shutdown;
	/**
	 * Creates a new Nuber region
	 * 
	 * @param dispatch The central dispatch to use for obtaining drivers, and logging events
	 * @param regionName The regions name, unique for the dispatch instance
	 * @param maxSimultaneousJobs The maximum number of simultaneous bookings the region is allowed to process
	 */
	public NuberRegion(NuberDispatch dispatch, String regionName, int maxSimultaneousJobs){
		this.dispatch = dispatch;
        this.regionName = regionName;
        this.maxSimultaneousJobs = maxSimultaneousJobs;
        this.waitingPassengers = new ConcurrentLinkedQueue<>();
        this.activeBookings = new AtomicInteger(0);
        this.shutdown = false;
	}
	
	/**
	 * Creates a booking for given passenger, and adds the booking to the 
	 * collection of jobs to process. Once the region has a position available, and a driver is available, 
	 * the booking should commence automatically. 
	 * 
	 * If the region has been told to shutdown, this function should return null, and log a message to the 
	 * console that the booking was rejected.
	 * 
	 * @param waitingPassenger
	 * @return a Future that will provide the final BookingResult object from the completed booking
	 */
	public Future<BookingResult> bookPassenger(Passenger waitingPassenger)
	{		
		if (shutdown) {
            dispatch.logEvent(null, "Booking rejected: Region is shut down");
            return CompletableFuture.completedFuture(null);
        }

        // Check if there is capacity to process a new booking
        if (activeBookings.get() >= maxSimultaneousJobs) {
            waitingPassengers.add(waitingPassenger); // Add to waiting passengers
            dispatch.logEvent(null, "Passenger added to waiting list: " + waitingPassenger);
            return CompletableFuture.supplyAsync(() -> {
                // Wait until there's a position available
                while (activeBookings.get() >= maxSimultaneousJobs) {
                    try {
                        Thread.sleep(100); // Wait before checking again
                    } catch (InterruptedException e) {
                        return null; // Return null if interrupted
                    }
                }
                return processBooking(waitingPassenger); // Process booking once a driver is available
            });
        } else {
            return CompletableFuture.supplyAsync(() -> processBooking(waitingPassenger));
        }
	}

	private BookingResult processBooking(Passenger passenger) {
        // Simulate getting a driver from the dispatch. This will block until a driver is available
        Driver driver = dispatch.getDriver();
        if (driver != null) {
            activeBookings.incrementAndGet();
            Booking booking = new Booking(dispatch, passenger);
            return booking.call(); // Calls the booking's call method to complete the booking
        } else {
            return null; // No driver available
        }
    }
	
	/**
	 * Called by dispatch to tell the region to complete its existing bookings and stop accepting any new bookings
	 */
	public void shutdown(){
		this.shutdown = true;
        dispatch.logEvent(null, "Region " + regionName + " is shutting down");
	}
		
}
