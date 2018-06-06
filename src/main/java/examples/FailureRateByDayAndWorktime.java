package examples;

import java.io.File;
import java.nio.file.Paths;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;

import cc.kave.commons.model.events.IIDEEvent;
import cc.kave.commons.utils.io.IReadingArchive;
import cc.kave.commons.utils.io.ReadingArchive;

public class FailureRateByDayAndWorktime {

	private class TrackUserChain{
		ZonedDateTime chainHead; 
		ZonedDateTime chainTail;
		double successCount = 0;
		double failureCount = 0;
		int totalTestBuildCount = 0;
		boolean isValid;
		
		
		
		
	}
	
	private class DayDurationBlock{
		double failureCount = 0;
		double successCount = 0;
		int totalTestBuildCount = 0;
		
		
		
	}
	
	
	private String dir;

	private Map<String, Integer> counts;
	
	public FailureRateByDayAndWorktime(String dir) {
		this.dir = dir;
	}
	
	public Map<String, Integer> getCounts(){
		return counts;
	}

	public void run() {
		Set<String> zips = IoHelper.findAllZips(dir);

		int zipTotal = zips.size();
		int zipCount = 0;
		DayDurationBlock[][] d = new DayDurationBlock[4][7];
		for(int i = 0; i < 4; i++)
		{
			for(int j = 0; j < 7; j++)
			{
				d[i][j] = new DayDurationBlock();
			}
		}
		for (String zip : zips) {
			double perc = 100 * zipCount / (double) zipTotal;
			zipCount++;

			System.out.printf("## %s, processing %s... (%d/%d, %.1f%% done)\n", new Date(), zip, zipCount, zipTotal,
					perc);
			File zipFile = Paths.get(dir, zip).toFile();

			int numEvents = 0;
			counts = Maps.newHashMap();
			
			Map<String, TrackUserChain> userCollection = Maps.newHashMap();

			
			int printCounter = 0;
			try (IReadingArchive ra = new ReadingArchive(zipFile)) {
				while (ra.hasNext()) {
					if (printCounter++ % 100 == 0) {
						System.out.printf(".");
					}
					numEvents++;
					IIDEEvent e = ra.getNext(IIDEEvent.class);
					String miningString = e.toString();
					//System.out.print(miningString);
					//System.out.print("Dividing line");
					/*if(e.getClass().getSimpleName().equals("TestRunEvent") || e.getClass().getSimpleName().equals("BuildEvent") ) 
							return;*/
					
					//String userID = miningString.substring(miningString.indexOf("IDESessionUUID="), miningString.indexOf("KaVEVersion") - 4);
					String timeString = miningString.substring(miningString.indexOf("TriggeredAt=") + 12, miningString.indexOf("TriggeredBy=") - 4);
					//LocalDate userDate = new LocalDate(Integer.parseInt(timeString.substring(0, 4)), Integer.parseInt(timeString.substring(0, 4)), Integer.parseInt(timeString.substring(0, 4)));
					
					String userID = miningString.substring(miningString.indexOf("IDESessionUUID=") + 15, miningString.indexOf("KaVEVersion=") - 4);
					
					
					
					//Code to get the userId and create a new instance in the hash map is there isn't one already goes here.
					

					//dateOfUse.
					
					//System.out.println(timeString);
					
					ZonedDateTime timeValue = ZonedDateTime.parse(timeString);					
					TrackUserChain rightChain = userCollection.get(userID);
					if (rightChain == null)
					{
						rightChain = new TrackUserChain();
						rightChain.isValid = false;
						rightChain.successCount = 0;
						rightChain.failureCount = 0; 
						rightChain.totalTestBuildCount = 0;
						
						userCollection.put(userID, rightChain);
					}
					
					
					if(rightChain.isValid){
						if(timeValue.compareTo(rightChain.chainTail.plusMinutes(5)) < 0) {
							rightChain.chainTail = timeValue;
						//	System.out.println("Should record success/failure here");
							if(e.getClass().getSimpleName().equals("TestRunEvent") || e.getClass().getSimpleName().equals("BuildEvent"))
							{
								
								 
								
								double successCount = miningResultCount(miningString, "Result=S"); 
								double failureCount = miningResultCount(miningString, "Result=") - successCount;
								failureCount += miningResultCount(miningString, "Successful=f");
								successCount += miningResultCount(miningString, "Successful=t");
								
								
								

								double failureRatio = failureCount / (successCount + failureCount);
								double successRatio = successCount / (successCount + failureCount);

								if(Double.isNaN((successRatio + failureRatio)))
								{
									//System.out.println("Here's where the problem happens. Success/Failure ratio is to blame");
									failureRatio = 0;
									successRatio = 0;
									rightChain.totalTestBuildCount -= 1;
								}
								rightChain.successCount += successRatio;
								rightChain.failureCount += failureRatio;
								
								rightChain.totalTestBuildCount += 1;
								
							}
						}
						else {
							rightChain.isValid = false;
							//System.out.println("Should clear and erase last instance's data.");
							int grouping = calculateTime(rightChain);
							//code to put the successes and failures in their proper places. 
							//Need to ensure that it's stored by date and consecutive work time.;
							int weekday = rightChain.chainHead.getDayOfWeek().getValue() - 1;
							if(rightChain.totalTestBuildCount > 0)
							{
								d[grouping][weekday].failureCount += rightChain.failureCount;
								d[grouping][weekday].successCount += rightChain.successCount;
								d[grouping][weekday].totalTestBuildCount += rightChain.totalTestBuildCount;
							}
							rightChain.isValid = false;
							rightChain.successCount = 0;
							rightChain.failureCount = 0; 
							rightChain.totalTestBuildCount = 0;

						}
					}
					if(!rightChain.isValid){
						rightChain.chainHead = timeValue;
						rightChain.chainTail = timeValue;
						rightChain.successCount = 0;
						rightChain.failureCount = 0; 
						rightChain.totalTestBuildCount = 0;
						if(e.getClass().getSimpleName().equals("TestRunEvent") || e.getClass().getSimpleName().equals("BuildEvent")) {
							double successCount = miningResultCount(miningString, "Successful=t");
							double failureCount = miningResultCount(miningString, "Successful=f");
							
							double failureRatio = failureCount / (successCount + failureCount);
							double successRatio = successCount / (successCount + failureCount);
							
							if(Double.isNaN((successRatio + failureRatio)))
							{
								//System.out.println("Here's where the problem happens. Success/Failure ratio is to blame");
								failureRatio = 0;
								successRatio = 0;
								rightChain.totalTestBuildCount -= 1;
							}
							
							rightChain.successCount += successRatio;
							rightChain.failureCount += failureRatio;
							
							rightChain.totalTestBuildCount += 1;
							/*
							System.out.print("Success count == ");
							System.out.println(rightChain.successCount);
							System.out.print("Failure count == ");
							System.out.println(rightChain.failureCount);
							System.out.println("Total test builds: ");
							System.out.println(rightChain.totalTestBuildCount);
							return;
							*/
						}
						rightChain.isValid = true;
								
					}
						
					
					String key = e.getClass().getSimpleName();
					Integer count = counts.get(key);
					if (count == null) {
						counts.put(key, 1);
					} else {
						counts.put(key, count + 1);
					}
				}
			}
			
			//TODO check all sessions that haven't been recorded as finished.
			//TODO and store results, and all that fun stuff.
			counts.put("<total>", numEvents);

			System.out.printf("\nFound the following events:\n");
			for (String key : counts.keySet()) {
				int count = counts.get(key);
				System.out.printf("%s: %d\n", key, count);
			}
			System.out.printf("\n");
			
			for (String key : userCollection.keySet()) {
				TrackUserChain rightChain = userCollection.get(key);
				if(rightChain.totalTestBuildCount > 0)
				{
					int weekday = rightChain.chainHead.getDayOfWeek().getValue() - 1;
					int grouping = calculateTime(rightChain);
					d[grouping][weekday].failureCount += rightChain.failureCount;
					d[grouping][weekday].successCount += rightChain.successCount;
					d[grouping][weekday].totalTestBuildCount += rightChain.totalTestBuildCount;
				}
			}
			for(int i = 0; i < 7; i++)
			{
				System.out.println(DayOfWeek.of(i + 1).toString());
				System.out.println("0-25 minute session numbers");
				System.out.println(d[0][i].failureCount);
				System.out.println(d[0][i].successCount);
				System.out.println(d[0][i].totalTestBuildCount);
				System.out.println("25-60 minute session numbers");
				System.out.println(d[1][i].failureCount);
				System.out.println(d[1][i].successCount);
				System.out.println(d[1][i].totalTestBuildCount);
				System.out.println("1-2 hour session numbers");
				System.out.println(d[2][i].failureCount);
				System.out.println(d[2][i].successCount);
				System.out.println(d[2][i].totalTestBuildCount);
				System.out.println("2+ hour session numbers");
				System.out.println(d[3][i].failureCount);
				System.out.println(d[3][i].successCount);
				System.out.println(d[3][i].totalTestBuildCount);
			}
			System.out.println();
		}
		
		System.out.printf("Done (%s)\n", new Date());
	}

	private int miningResultCount(String miningString, String huntedString) {
		// TODO Auto-generated method stub
		int i = 0;
		int countInstances = 0;
		while (i > -1 && i < miningString.length()){
			int j = miningString.indexOf(huntedString, i);
			if (j > -1){	
				i = j + huntedString.length();
				countInstances++;
			}
			else {
				i = -1;
			}
		}
		return countInstances;
	}

	private int calculateTime(TrackUserChain rightChain) {
		// TODO Auto-generated method stub
		if(rightChain.chainTail.compareTo(rightChain.chainHead.plusMinutes(25)) < 0)
			return 0;
		if(rightChain.chainTail.compareTo(rightChain.chainHead.plusMinutes(60)) < 0)
			return 1;
		if(rightChain.chainTail.compareTo(rightChain.chainHead.plusMinutes(120)) < 0)
			return 2;
		return 3;
	}
}