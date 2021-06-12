import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;

import static java.lang.Double.parseDouble;

public class MarketHandler implements Runnable
{
	private static final String sBaronKey = "1602273-1b80c4a8-4d4d-4ecc-9eb5-58a085cb4a45";

	private final String skinName;
	private final double maxPrice;
	private final double maxWear;

	MarketHandler(String skinName, double maxPrice, double maxWear)
	{
		this.skinName = skinName;
		this.maxPrice = maxPrice;
		this.maxWear = maxWear;
	}

	@Override
	public void run()
	{
		System.out.println(Thread.currentThread().getName() + " - " + skinName + "/" + maxWear + "/" + maxPrice);
		ArrayList<String> listings = search(skinName, maxWear, maxPrice);

		for (int i = 0; i < listings.size(); i++)
		{
			String skinInfo = listings.get(i);
			String[] split = skinInfo.split(",");
			if (listings.size() > 1)
			{
				DiscordWebhook fail = new DiscordWebhook(
						"https://discord.com/api/webhooks/849714479695921192/57wLUno4WezBhZW3L_B7OLngBkrHwsWok6diIlzDj6QjEBxfVUVs9ygAqGlisK4lo4RW");
				fail.setContent("FAIL SAFE: Prevented buying " + split[0] + " for \u20ac" + split[2]);
				try
				{
					fail.execute();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				Thread.currentThread().interrupt();
			}

			if (getBalance() > parseDouble(split[2]))
			{
				DiscordWebhook attempt = new DiscordWebhook(
						"https://discord.com/api/webhooks/849714479695921192/57wLUno4WezBhZW3L_B7OLngBkrHwsWok6diIlzDj6QjEBxfVUVs9ygAqGlisK4lo4RW");
				attempt.addEmbed(new DiscordWebhook.EmbedObject()
						.setAuthor("Attempt", "https://skinbaron.de/en/profile/inventory", "https://ibb.co/0ndJ4JX")
						.setColor(Color.ORANGE)
						.addField("Skin", split[0], true)
						.addField("Price", "\u20ac" + split[2], true));
				try
				{
					attempt.execute();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				String purchaseResponse = purchase(split[1], Double.valueOf(split[2]));
				DiscordWebhook purchase = new DiscordWebhook(
						"https://discord.com/api/webhooks/849714479695921192/57wLUno4WezBhZW3L_B7OLngBkrHwsWok6diIlzDj6QjEBxfVUVs9ygAqGlisK4lo4RW");
				purchase.addEmbed(new DiscordWebhook.EmbedObject()
						.setAuthor("Purchase", "https://skinbaron.de/en/profile/inventory", "https://ibb.co/0ndJ4JX")
						.setColor(Color.GREEN)
						.addField("Skin", split[0], true)
						.addField("Price", "\u20ac" + split[2], true)
						.addField("Balance", "\u20ac" + getBalance(), false)
						.addField("Response", purchaseResponse, false));
				try
				{
					purchase.execute();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
			else
			{
				DecimalFormat balanceFormat = new DecimalFormat("#.##");
				balanceFormat.setRoundingMode(RoundingMode.FLOOR);

				String purchaseResponse = purchase(split[1], Double.valueOf(split[2]));
				if (purchaseResponse.equals(
						"{ \"generalErrors\": [ \"some offer(s) already in another shopping cart and/or sold\" ] }"))
				{
					try
					{
						FileWriter myWriter = new FileWriter("log.txt");
						myWriter.write(
								"Item sold: " + split[0] + ", Price: " + split[2] + ", Balance: " + balanceFormat.format(
										getBalance()));
						myWriter.close();
					}
					catch (IOException e)
					{
						System.out.println("An error occurred.");
						e.printStackTrace();
					}
				}
				else
				{

					DiscordWebhook attempt = new DiscordWebhook(
							"https://discord.com/api/webhooks/849714479695921192/57wLUno4WezBhZW3L_B7OLngBkrHwsWok6diIlzDj6QjEBxfVUVs9ygAqGlisK4lo4RW");
					attempt.addEmbed(new DiscordWebhook.EmbedObject()
							.setAuthor("Attempt", "https://skinbaron.de/en/profile/inventory", "https://ibb.co/0ndJ4JX")
							.setColor(java.awt.Color.ORANGE)
							.addField("Skin", split[0], true)
							.addField("Price", split[2], false)
							.addField("Balance", String.valueOf(balanceFormat.format(getBalance())), true));
					try
					{
						attempt.execute();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}
			}
		}
	}

	private ArrayList<String> search(String skinName, double maxWear, double maxPrice)
	{
		HttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom()
						.setCookieSpec(CookieSpecs.STANDARD).build())
				.build();
		String json = "{\n" +
				"  \"apikey\": \"" + sBaronKey + "\",\n" +
				"  \"appid\": 730,\n" +
				"  \"search_item\": \"" + skinName + "\",\n" +
				"  \"min\": 0,\n" +
				"  \"max\": " + maxPrice + "\n" +
				"}";
		String responseBody;
		ArrayList<String> listings = new ArrayList<>();
		try
		{

			HttpEntity entity = new StringEntity(json);
			HttpUriRequest request = RequestBuilder.post()
					.setUri("https://api.skinbaron.de/Search")
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.setHeader("x-requested-with", "XMLHttpRequest")
					.setEntity(entity)
					.build();
			HttpResponse response = client.execute(request);
			responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
			try
			{
				JsonElement element = JsonParser.parseString(responseBody);
				JsonObject jsonObject = element.getAsJsonObject();
				JsonArray sales = jsonObject.get("sales").getAsJsonArray();
				for (int i = 0; i < sales.size(); i++)
				{
					JsonObject listing = sales.get(i).getAsJsonObject();
					float wear = listing.get("wear").getAsFloat();
					float price = listing.get("price").getAsFloat();
					if (wear > maxWear)
					{
						continue;
					}
					String id = listing.get("id").getAsString();
					String name = listing.get("market_name").getAsString();
					listings.add(name + "," + id + "," + price + "," + wear);
				}
			}
			catch (JsonSyntaxException e)
			{
				System.out.println("Rate limit");
			}

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return listings;
	}


	private String purchase(String id, Double total)
	{
		HttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom()
						.setCookieSpec(CookieSpecs.STANDARD).build())
				.build();
		String json = "{\n" +
				"   \"apikey\":\"" + sBaronKey + "\",\n" +
				"   \"total\": " + total + ",\n" +
				"   \"saleids\":[\n" +
				"      \"" + id + "\"\n" +
				"   ]\n" +
				"}";
		String responseBody = "Purchase failed before try statement.";
		try
		{
			HttpEntity entity = new StringEntity(json);
			HttpUriRequest request = RequestBuilder.post()
					.setUri("https://api.skinbaron.de/BuyItems")
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.setHeader("x-requested-with", "XMLHttpRequest")
					.setEntity(entity)
					.build();
			HttpResponse response = client.execute(request);
			responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return responseBody;
	}


	private double getBalance()
	{
		HttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom()
						.setCookieSpec(CookieSpecs.STANDARD).build())
				.build();
		String json = "{\n" +
				"  \"apikey\": \"" + sBaronKey + "\"\n" +
				"}";
		float balance = 0;
		try
		{
			HttpEntity entity = new StringEntity(json);
			HttpUriRequest request = RequestBuilder.post()
					.setUri("https://api.skinbaron.de/GetBalance")
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.setHeader("x-requested-with", "XMLHttpRequest")
					.setEntity(entity)
					.build();
			HttpResponse response = client.execute(request);
			String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
			JsonElement jsonElement = JsonParser.parseString(responseBody);
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			balance = jsonObject.get("balance").getAsFloat();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return balance;
	}
}
