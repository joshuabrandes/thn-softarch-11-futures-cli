package ohm.softa.a11;

import ohm.softa.a11.openmensa.OpenMensaAPI;
import ohm.softa.a11.openmensa.OpenMensaAPIService;
import ohm.softa.a11.openmensa.model.Canteen;
import ohm.softa.a11.openmensa.model.Meal;
import ohm.softa.a11.openmensa.model.PageInfo;
import retrofit2.Response;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/**
 * @author Peter Kurfer
 * Created on 12/16/17.
 */
public class App2 {
	private static final String OPEN_MENSA_DATE_FORMAT = "yyyy-MM-dd";

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat(OPEN_MENSA_DATE_FORMAT, Locale.getDefault());
	private static final Scanner inputScanner = new Scanner(System.in);
	private static final OpenMensaAPI openMensaAPI = OpenMensaAPIService.getInstance().getOpenMensaAPI();
	private static final Calendar currentDate = Calendar.getInstance();
	private static int currentCanteenId = -1;

	public static void main(String[] args) throws ExecutionException, InterruptedException {
		MenuSelection selection;
		/* loop while true to get back to the menu every time an action was performed */
		do {
			selection = menu();
			switch (selection) {
				case SHOW_CANTEENS:
					printCanteens();
					break;
				case SET_CANTEEN:
					readCanteen();
					break;
				case SHOW_MEALS:
					printMeals();
					break;
				case SET_DATE:
					readDate();
					break;
				case QUIT:
					System.exit(0);

			}
		} while (true);
	}

	private static void printCanteens() throws ExecutionException, InterruptedException {
		System.out.print("Fetching canteens [");
		/* TODO fetch all canteens and print them to STDOUT
		 * at first get a page without an index to be able to extract the required pagination information
		 * afterwards you can iterate the remaining pages
		 * keep in mind that you should await the process as the user has to select canteen with a specific id */
		openMensaAPI.getCanteens()
			.thenApply(response -> {
				var pageInfo = PageInfo.extractFromResponse(response);
				List<Canteen> canteens;

				if (response.body() == null) {
					canteens = new LinkedList<>();
				} else {
					canteens = response.body();
				}

				CompletableFuture<List<Canteen>> canteensFuture = null;

				for (int i = 2; i < pageInfo.getTotalCountOfPages(); i++) {
					if (canteensFuture == null) {
						canteensFuture = openMensaAPI.getCanteens(i);
					} else {
						canteensFuture = canteensFuture.thenCombine(
							openMensaAPI.getCanteens(i),
							(list1, list2) -> {
								list1.addAll(list2);
								return list1;
							}
						);
					}

				}
				try {
					canteens.addAll(canteensFuture.get());
				} catch (InterruptedException | ExecutionException ex) {
					ex.printStackTrace();
				}

				return canteens;
			})
			.thenAccept(canteens -> canteens.forEach(System.out::println))
			.get();
	}

	private static void printMeals() throws ExecutionException, InterruptedException {
		/* TODO fetch all meals for the currently selected canteen
		 * to avoid errors retrieve at first the state of the canteen and check if the canteen is opened at the selected day
		 * don't forget to check if a canteen was selected previously! */
		if (currentCanteenId < 0) {
			System.out.println("No canteen is selected.");
			return;
		}

		String date = dateFormat.format(currentDate.getTime());

		openMensaAPI.getCanteenState(currentCanteenId, date)
			.thenApply(state -> {
				if (state != null || !state.isClosed()) {
					try {
						return openMensaAPI.getMeals(currentCanteenId, date).get();
					} catch (InterruptedException | ExecutionException ex) {
						ex.printStackTrace();
					}
				} else {
					System.out.println("Seems like the canteen is currently closed.");
				}

				return new LinkedList<Meal>();
			})
			.thenAccept(meals -> meals.forEach(System.out::println))
			.get();
	}

	/**
	 * Utility method to select a canteen
	 */
	private static void readCanteen() {
		/* typical input reading pattern */
		boolean readCanteenId = false;
		do {
			try {
				System.out.println("Enter canteen id:");
				currentCanteenId = inputScanner.nextInt();
				readCanteenId = true;
			} catch (Exception e) {
				System.out.println("Sorry could not read the canteen id");
			}
		} while (!readCanteenId);
	}

	/**
	 * Utility method to read a date and update the calendar
	 */
	private static void readDate() {
		/* typical input reading pattern */
		boolean readDate = false;
		do {
			try {
				System.out.println("Pleae enter date in the format yyyy-mm-dd:");
				Date d = dateFormat.parse(inputScanner.next());
				currentDate.setTime(d);
				readDate = true;
			} catch (ParseException p) {
				System.out.println("Sorry, the entered date could not be parsed.");
			}
		} while (!readDate);

	}

	/**
	 * Utility method to print menu and read the user selection
	 *
	 * @return user selection as MenuSelection
	 */
	private static MenuSelection menu() {
		IntStream.range(0, 20).forEach(i -> System.out.print("#"));
		System.out.println();
		System.out.println("1) Show canteens");
		System.out.println("2) Set canteen");
		System.out.println("3) Show meals");
		System.out.println("4) Set date");
		System.out.println("5) Quit");
		IntStream.range(0, 20).forEach(i -> System.out.print("#"));
		System.out.println();

		switch (inputScanner.nextInt()) {
			case 1:
				return MenuSelection.SHOW_CANTEENS;
			case 2:
				return MenuSelection.SET_CANTEEN;
			case 3:
				return MenuSelection.SHOW_MEALS;
			case 4:
				return MenuSelection.SET_DATE;
			default:
				return MenuSelection.QUIT;
		}
	}
}
