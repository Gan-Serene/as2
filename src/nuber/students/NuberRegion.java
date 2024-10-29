package nuber.students;

import java.util.concurrent.atomic.AtomicInteger;

public class NuberRegion {
    
    // Reference to the main dispatch system managing all regions
    private final NuberDispatch dispatch;
    // Name of the region (e.g., "North", "South")
    private final String regionName;
    // Maximum simultaneous jobs allowed for this region
    private final int maxSimultaneousJobs;
    // Counter for current active bookings in this region
    private final AtomicInteger currentBookings;
    // Flag indicating whether this region is shut down
    volatile boolean shutdown = false;

    /**
     * Constructor for NuberRegion, initializing the region with dispatch reference,
     * region name, maximum allowed bookings, and setting initial booking count to 0.
     *
     * @param dispatch Reference to the main dispatch system
     * @param regionName Name of the region
     * @param maxSimultaneousJobs Maximum number of simultaneous jobs allowed
     */
    public NuberRegion(NuberDispatch dispatch, String regionName, int maxSimultaneousJobs) {
        this.dispatch = dispatch;
        this.regionName = regionName;
        this.maxSimultaneousJobs = maxSimultaneousJobs;
        this.currentBookings = new AtomicInteger(0); // Initialize bookings to zero
    }

    /**
     * Checks if the region is currently shut down.
     *
     * @return true if the region is shut down, false otherwise
     */
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Shuts down the region, preventing it from accepting new bookings.
     * Logs the shutdown event.
     */
    public void shutdown() {
        this.shutdown = true;
        dispatch.logEvent(null, "Region " + regionName + " is shutting down");
    }

    /**
     * Checks if the region can accept a new booking.
     * 
     * A region can accept a booking if the number of current bookings
     * is below the maximum allowed and the region is not shut down.
     *
     * @return true if the region can accept a new booking, false otherwise
     */
    public boolean canAcceptBooking() {
        return currentBookings.get() < maxSimultaneousJobs && !shutdown;
    }

    /**
     * Increments the count of active bookings for this region.
     */
    public void incrementBookingCount() {
        currentBookings.incrementAndGet();
    }

    /**
     * Decrements the count of active bookings for this region.
     */
    public void decrementBookingCount() {
        currentBookings.decrementAndGet();
    }

    /**
     * Returns the current number of active bookings in this region.
     *
     * @return the current active booking count
     */
    public int getCurrentBookings() {
        return currentBookings.get();
    }

    /**
     * Provides a string representation of the NuberRegion, including
     * its name and maximum simultaneous jobs allowed.
     *
     * @return a string representation of the NuberRegion object
     */
    @Override
    public String toString() {
        return "NuberRegion{" +
                "regionName='" + regionName + '\'' +
                ", maxSimultaneousJobs=" + maxSimultaneousJobs +
                '}';
    }
}
