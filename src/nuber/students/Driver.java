package nuber.students;

public class Driver extends Person {
	private Passenger passenger;
	
	public Driver(String driverName, int maxSleep)
	{
		super(driverName,maxSleep);
	}
	
	/**
	 * Stores the provided passenger as the driver's current passenger and then
	 * sleeps the thread for between 0-maxDelay milliseconds.
	 * 
	 * @param newPassenger Passenger to collect
	 * @throws InterruptedException
	 */
	public void pickUpPassenger(Passenger newPassenger)
	{
		this.passenger = newPassenger;
		try {
			int delay = (int) (Math.random() * maxSleep);
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			System.out.println("Pickup interrupted.");
		}
	}

	/**
	 * Sleeps the thread for the amount of time returned by the current 
	 * passenger's getTravelTime() function
	 * 
	 * @throws InterruptedException
	 */
	public void driveToDestination() {
		if (passenger != null) {
			int travelTime = passenger.getTravelTime();
			try {
				Thread.sleep(travelTime);
			} catch (InterruptedException e) {
				System.out.println("Drive interrupted.");
			}
		} else {
			System.out.println("No passenger to drive.");
		}
	}
	
}
