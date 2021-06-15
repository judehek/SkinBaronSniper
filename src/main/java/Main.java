import com.google.gson.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main
{
	public static final String pEmpireKey = "68cef992-9cca-4d1d-81bb-0a11ffc74185";
	public static int servers;
	public static int serverNumber;
	public static int startRange;
	public static int endRange;
	public static ArrayList<String> settings;
	public static ArrayList<Double> maxPrices;

	public static void main(String[] args) throws InterruptedException, IOException
	{

		System.out.println("Version: 3.5");
		//System.setProperty("webdriver.gecko.driver", "H:\\IntelliJ Projects\\SkinBaronBot\\driver\\geckodriver.exe");
		System.setProperty("webdriver.gecko.driver", "/root/skinbaron/driver/geckodriver");

		boolean useApi = false;

		settings = checkSettings();
		maxPrices = new ArrayList<>();
		servers = Integer.parseInt(settings.get(1));
		serverNumber = checkServerNumber();
		//serverNumber = 1;
		System.out.println("Server Number: " + serverNumber);

		startRange = (((settings.size() - 2) / servers) * (serverNumber - 1));
		endRange = (startRange + (settings.size() - 2) / servers);

		ExecutorService executor = Executors.newFixedThreadPool((settings.size() - 2) / servers);

		if (settings.get(0).equals("true"))
		{
			useApi = true;
		}
		servers = Integer.parseInt(settings.get(1));

		maxPriceSetup();

		try
		{
			Timer timer = new Timer();
			boolean finalUseApi = useApi;
			TimerTask dailyUpdate = new TimerTask()
			{
				@Override
				public void run()
				{
					ArrayList<String> oldPrices = (ArrayList<String>) maxPrices.clone();
					WebDriver driver = new FirefoxDriver();
					for (int i = startRange; i < endRange; i++)
					{
						String skinName = settings.get(i + 2);
						if (finalUseApi)
						{
							maxPrices.set(i, empirePriceApi(skinName));
						}
						else
						{
							maxPrices.set(i, empirePriceScrape(driver, skinName));
						}
					}
					driver.close();

					//saveTimeStamp();

					printChanges(oldPrices);


				}

			};
			TimerTask settingsCheck = new TimerTask()
			{
				@Override
				public void run()
				{
					ArrayList<String> settingsUpdatedCheck = checkSettings();
					ArrayList<String> oldSettings = (ArrayList<String>) settings.clone();
					if (settingsUpdatedCheck != oldSettings)
					{
						Collection<String> difference = CollectionUtils.subtract(settingsUpdatedCheck, oldSettings);
						ArrayList<String> differenceConverted = (ArrayList<String>) difference;
						WebDriver driver = new FirefoxDriver();
						for (int i = 0; i < differenceConverted.size(); i++)
						{
							System.out.println("next skin " + i);
							settings.add(differenceConverted.get(i));
							if (finalUseApi)
							{
								maxPrices.set(i, empirePriceApi(differenceConverted.get(i)));
							}
							else
							{
								maxPrices.set(i, empirePriceScrape(driver, differenceConverted.get(i)));
							}
						}
						driver.close();
					}
					//for (int i = startRange; i < endRange; i++)
					//{
					//	System.out.println((settings.get(i + 2)) + ": " + maxPrices.get(i));
					//}
				}
			};

			timer.schedule(dailyUpdate, 1, 1000 * 60 * 60 * 24);
			timer.schedule(settingsCheck, 1, 1000 * 60 * 5);
		}
		catch (RuntimeException e)
		{
			DiscordWebhook errors = new DiscordWebhook(
					"https://discord.com/api/webhooks/849714479695921192/57wLUno4WezBhZW3L_B7OLngBkrHwsWok6diIlzDj6QjEBxfVUVs9ygAqGlisK4lo4RW");
			errors.setContent("Runtime Exception " + e.getStackTrace());
			errors.execute();
			return;
		}
		catch (Throwable e)
		{
			DiscordWebhook errors = new DiscordWebhook(
					"https://discord.com/api/webhooks/849714479695921192/57wLUno4WezBhZW3L_B7OLngBkrHwsWok6diIlzDj6QjEBxfVUVs9ygAqGlisK4lo4RW");
			errors.setContent("Throwable " + e.getStackTrace());
			errors.execute();
			throw e;
		}

		while (true)
		{
			for (int i = startRange; i < endRange; i++) //(Gun | Skin,Maximum float,Maximum price)
			{
				if (i == 1)
				{
					Thread.sleep(1000);
				}
				String fullString = settings.get(i + 2);
				String skinNoWear = fullString.substring(0, fullString.indexOf(" ("));
				String skinName = skinNoWear.replace("\u2605 ", "");

				double maxPrice = maxPrices.get(i);
				double maxWear = checkFloat(fullString);

				Runnable worker = new MarketHandler(skinName, maxPrice, maxWear);
				executor.execute(worker);

				Thread.sleep(550);
			}
		}
	}

	public static ArrayList<String> checkSettings()
	{
		HttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom()
						.setCookieSpec(CookieSpecs.STANDARD).build())
				.build();
		ArrayList<String> settings = new ArrayList<>();
		try
		{
			HttpUriRequest request = RequestBuilder.get()
					.setUri("https://jsonblob.com/api/c6b0d896-67d7-11eb-9c90-d370c73b634a")
					.build();
			HttpResponse response = client.execute(request);
			String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
			JsonElement jsonElement = JsonParser.parseString(responseBody);
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			settings.add(String.valueOf(jsonObject.get("api").getAsBoolean()));
			settings.add(String.valueOf(jsonObject.get("servers").getAsInt()));
			JsonArray skins = jsonObject.get("skins").getAsJsonArray();
			for (int i = 0; i < skins.size(); i++)
			{
				settings.add(String.valueOf(skins.get(i)).replace("\"", ""));
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return settings;
	}

	private static double empirePriceApi(String item)
	{
		HttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom()
						.setCookieSpec(CookieSpecs.STANDARD).build())
				.build();
		double price = 0;
		try
		{
			HttpUriRequest request = RequestBuilder.get()
					.setUri("https://api.pricempire.com/v1/getPrices/" + item + "?token=" + Main.pEmpireKey + "&currency=EUR")
					.build();
			HttpResponse response = client.execute(request);
			String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
			JsonElement jsonElement = JsonParser.parseString(responseBody);
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			price = (jsonObject.get("").getAsDouble() * 0.81);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return price;
	}

	private static double empirePriceScrape(WebDriver driver, String skinName)
	{
		try
		{
			Thread.sleep(5000);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		double discountedPrice;

		driver.get("https://pricempire.com/item/" + skinName);

		new WebDriverWait(driver, 60).until(
				ExpectedConditions.visibilityOfAllElementsLocatedBy(By.className("--price")));
		List<WebElement> priceProviders = driver.findElements(By.className("--price"));

		//new WebDriverWait(driver, 60).until(
		//		ExpectedConditions.visibilityOfAllElementsLocatedBy(By.className("lite-price")));
		//List<WebElement> priceProviders = driver.findElements(By.className("lite-price"));

		//ArrayList<String> names = new ArrayList<>();
		//ArrayList<String> prices = new ArrayList<>();

		/*String price = "";
		for (int i = 0; i < priceProviders.size(); i++)
		{
			names.add(priceProviders.get(i).findElement(By.className("name")).getText());
			prices.add(priceProviders.get(i).findElement(By.className("price")).getText());

			if (names.get(i).equalsIgnoreCase("buff"))
			{
				price = prices.get(i);
				break;
			}
		}*/

		String allPrice = priceProviders.get(0).getText();
		int subInt = allPrice.indexOf("Buff") + 6;
		String buffEndString = allPrice.substring(subInt);
		int buffEnd = buffEndString.indexOf(".") + 3;
		String price = buffEndString.substring(0, buffEnd);
		System.out.println("\n\n\n\n\n\n\n\n" + allPrice);
		if (price.contains("hase"))
		{
			price = price.substring(8);
		}
		else
		{
			price = price.substring(8);
		}
		System.out.println("\n\n\n\n\n\n\n\n" + price);
		String priceRemoveCommas = price.replace(",", "");
		//discountedPrice = Double.parseDouble(priceRemoveCommas.substring(1)) * 0.84;
		discountedPrice = Double.parseDouble(priceRemoveCommas) * 0.81;
		return usdToEuro(discountedPrice);
	}

	public static double usdToEuro(double usd)
	{
		HttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom()
						.setCookieSpec(CookieSpecs.STANDARD).build())
				.build();

		double euro = 0;
		try
		{
			HttpUriRequest request = RequestBuilder.get()
					.setUri("https://free.currconv.com/api/v7/convert?q=USD_EUR&compact=ultra&apiKey=89163e8fa71ae09917f9")
					.build();
			HttpResponse response = client.execute(request);
			String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
			JsonElement jsonElement = JsonParser.parseString(responseBody);
			JsonObject object = jsonElement.getAsJsonObject();
			double euroRate = object.get("USD_EUR").getAsDouble();

			DecimalFormat maxPriceFormat = new DecimalFormat("#.##");
			maxPriceFormat.setRoundingMode(RoundingMode.FLOOR);

			double euroUnformatted = usd * euroRate;
			euro = Double.parseDouble(maxPriceFormat.format(euroUnformatted));
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return euro;
	}

	private static void saveTimeStamp()
	{
		String currentTime = ZonedDateTime.now(ZoneId.of("America/Denver")).format(
				DateTimeFormatter.ofPattern("uuuu.MM.dd.HH.mm.ss"));

		ArrayList<String> timestampsString = new ArrayList<>();
		for (int i = 0; i < settings.size(); i++)
		{
			timestampsString.add(".");
		}
		for (int i = 0; i < settings.size(); i++)
		{
			timestampsString.set(i, (settings.get(i).replace("\u2605 ", "")) + "," + maxPrices.get(i));
		}
		HttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom()
						.setCookieSpec(CookieSpecs.STANDARD).build())
				.build();
		try
		{
			HttpUriRequest getBlob = RequestBuilder.get()
					.setUri("https://jsonblob.com/api/42077f3f-7089-11eb-9c83-4b5097da1186")
					.build();
			HttpResponse response = client.execute(getBlob);
			String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
			JsonElement jsonElement = JsonParser.parseString(responseBody);
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			JsonArray timestamps = jsonObject.get("timestamps").getAsJsonArray();
			ArrayList serializeTimeStamps = new ArrayList();
			for (int i = 0; i < timestamps.size(); i++)
			{
				serializeTimeStamps.add(timestamps.get(i));
			}
			Gson gson = new Gson();
			String json;
			for (int i = 1; i < timestampsString.size(); i++)
			{
				serializeTimeStamps.add(timestampsString.get(i) + "," + currentTime);
			}
			json = gson.toJson(serializeTimeStamps);
			StringEntity entity = new StringEntity("{\"timestamps\":" + json + "}");
			HttpPut uploadBlob = new HttpPut("https://jsonblob.com/api/42077f3f-7089-11eb-9c83-4b5097da1186");
			uploadBlob.setEntity(entity);
			uploadBlob.setHeader("Content-Type", "application/json");
			HttpResponse uploadResponse = client.execute(uploadBlob);
			String uploadStringResponse = EntityUtils.toString(uploadResponse.getEntity(), StandardCharsets.UTF_8);
			System.out.println(uploadStringResponse);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	private static double checkFloat(String skinName)
	{
		double maxWear;

		String wearInLetters = skinName.substring(skinName.indexOf("(") + 1, skinName.indexOf(")"));

		switch (wearInLetters)
		{
			case "Factory New":
				maxWear = 0.07;
				break;
			case "Minimal Wear":
				maxWear = 0.15;
				break;
			case "Field-Tested":
				maxWear = 0.38;
				break;
			case "Well-Worn":
				maxWear = 0.45;
				break;
			case "Battle-Scarred":
				maxWear = 1.00;
				break;
			default:
				System.out.println(skinName + " invalid float value");
				maxWear = 0.01;
		}

		return maxWear;
	}

	private static void printChanges(ArrayList<String> oldPrices)
	{
		String json = "";
		for (int i = startRange; i < endRange; i++)
		{
			String skinName = settings.get(i + 2).substring(2);
			json = "{" +
					"\"content\": " + "\""
					+ skinName + " " + String.valueOf(oldPrices.get(i)) +
					"  :arrow_right:  " + String.valueOf(maxPrices.get(i)) + "\"}";

			HttpClient client = HttpClients.custom()
					.setDefaultRequestConfig(RequestConfig.custom()
							.setCookieSpec(CookieSpecs.STANDARD).build())
					.build();
			try
			{
				HttpEntity entity = new StringEntity(json);
				HttpUriRequest request = RequestBuilder.post()
						.setUri("https://discord.com/api/webhooks/849714479695921192/57wLUno4WezBhZW3L_B7OLngBkrHwsWok6diIlzDj6QjEBxfVUVs9ygAqGlisK4lo4RW")
						.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
						.setEntity(entity)
						.build();
				HttpResponse response = client.execute(request);
				System.out.println(response);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	private static int checkServerNumber() throws IOException
	{
		try
		{
			String server;
			BufferedReader fileReader = new BufferedReader(new FileReader("/root/skinbaron/config.txt"));
			server = fileReader.readLine();
			return Integer.parseInt(server.substring(14));
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		return -1;
	}

	private static void maxPriceSetup()
	{
		for (int i = 0; i < 100; i++)
		{
			maxPrices.add(1.69);
		}
	}

}

