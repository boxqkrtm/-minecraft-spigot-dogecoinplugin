package whitetac.spigotplugin.mcdogecoin;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.permission.Permission;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class App extends JavaPlugin
{
    private static Economy econ = null;
    public double coinPrice = 8000.0;
    private static Permission perms = null;
    private static Chat chat = null;
    FileConfiguration config = getConfig();
    long lastRefreshTime = 0;

    @Override
    public void onEnable() {
        getLogger().info("Enable Mc Dogecoin trader by whitetac");
        if (!setupEconomy() ) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try{
            coinPrice = config.getDouble("doge");
        }catch(Exception e){
            config.addDefault("doge", (double) 8000.0);
            saveConfig();
        }
        try{
            lastRefreshTime = config.getLong("lastRefreshTime");
        }catch(Exception e){
            config.addDefault("lastRefreshTime", 0);
            saveConfig();
        }
    }
    @Override
    public void onDisable() {
        getLogger().info("Disable Mc dogecoin trader by whitetac");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }
    
    /*private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();
        return perms != null;
    }*/
    
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if(!(sender instanceof Player)) {
            getLogger().info("Only players are supported for this Example Plugin, but you should not do this!!!");
            return true;
        }

        if(lastRefreshTime+1000*5<System.currentTimeMillis()) {
            //check every 5secs
            getPrice();
            if(coinPrice<=1){
                sender.sendMessage(String.format("오류발생."));
                return true;
            }
        }
        
        Player player = (Player) sender;
        OfflinePlayer offlinePlayer = (OfflinePlayer) sender;
        String uuid = player.getUniqueId().toString();
        
        double coinAmount;
        try{
            coinAmount = config.getDouble(uuid);
        }catch(Exception e){
            sender.sendMessage(String.format("비트코인 지갑이 생성되었습니다."));
            config.addDefault(uuid, (double) 0.0);
            config.set(uuid, (double)0.0);
            coinAmount = 0;
        }
        
        
        if(command.getLabel().equals("doge")) {
            
            // Lets give the player 1.05 currency (note that SOME economic plugins require rounding!)
            //econ.format(econ.getBalance(offlinePlayer.getName())
            double moneyAmount = econ.getBalance(offlinePlayer);
            if(args.length == 2){
                switch(args[0]){
                    case "buy":
                        double amount = Math.round(Double.parseDouble(args[1])*1000.0)/1000.0;
                        if(amount <= moneyAmount/coinPrice){
                            EconomyResponse r = econ.withdrawPlayer(player, coinPrice*amount);
                            if(r.transactionSuccess()) {
                                sender.sendMessage(String.format("----------매수 승인----------%s",amount));
                                double result = config.getDouble(uuid) + amount;
                                config.set(uuid, result);
                                coinAmount = result;
                                moneyAmount = econ.getBalance(offlinePlayer);
                            } else {
                                sender.sendMessage("----------매수 오류----------");
                            }
                        }else{
                            sender.sendMessage("----------잔액부족----------");
                        }
                    break;
                    case "sell":
                    amount = Math.round(Double.parseDouble(args[1])*1000.0)/1000.0;
                        if(amount <= coinAmount){
                            EconomyResponse r = econ.depositPlayer(player, amount*coinPrice);
                            if(r.transactionSuccess()) {
                                sender.sendMessage("----------매도 승인----------");
                                double result = config.getDouble(uuid) - amount;
                                config.set(uuid, result);
                                coinAmount = result;
                                moneyAmount = econ.getBalance(offlinePlayer);
                            } else {
                                sender.sendMessage("----------매도 오류----------");
                            }
                        }else{
                            sender.sendMessage("----------잔액부족----------");
                        }
                    break;
                }
            }
            sender.sendMessage("------------------------------");
            sender.sendMessage(String.format("도지코인 시세정보는 업비트api 기반으로 이루어집니다. (최소 수량 0.001)"));
            sender.sendMessage(String.format("거래방법 /doge buy|sell 수량"));
            sender.sendMessage(String.format("시세 DOGE/MD %s", coinPrice));
            sender.sendMessage(String.format("보유 MD %s", econ.format(moneyAmount)));
            sender.sendMessage(String.format("보유 코인 %sDOGE", Math.round(coinAmount*1000.0)/1000.0));
            sender.sendMessage(String.format(" "));
            sender.sendMessage(String.format("구매가능 수량 %sDOGE", Math.floor((moneyAmount/coinPrice)*1000.0)/1000.0));
            sender.sendMessage(String.format("보유 코인 가치 %sMD", Math.round((coinAmount*coinPrice)*1000.0)/1000.0));
            sender.sendMessage("------------------------------");
            
            //결과 저장
            saveConfig();
            return true;
        } else {
            return false;
        }
    }
    
    public static Economy getEconomy() {
        return econ;
    }
    
    public static Permission getPermissions() {
        return perms;
    }
    
    public static Chat getChat() {
        return chat;
    }

    public void getPrice() {
        //1day 100request limit
        //will request per 5sec
        

        HttpURLConnection conn = null;
        
        try {
            //URL 설정
            URL url = new URL("https://api.upbit.com/v1/candles/minutes/1?market=KRW-DOGE&count=1");
    
            conn = (HttpURLConnection) url.openConnection();
            //Request 형식 설정
            //conn.setDoOutput(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36");
    
            //request에 JSON data 준비
            
            //보내고 결과값 받기
            int responseCode = conn.getResponseCode();
            if (responseCode == 400) {
                getLogger().info("mcBtcTrade 400:: 해당 명령을 실행할 수 없음 (실행할 수 없는 상태일 때, 엘리베이터 수와 Command 수가 일치하지 않을 때, 엘리베이터 정원을 초과하여 태울 때)");
            }else if (responseCode == 405){
                getLogger().info("mcBtcTrade 405 에러");
            } else if (responseCode == 401) {
                getLogger().info("mcBtcTrade 401:: X-Auth-Token Header가 잘못됨");
            } else if (responseCode == 500) {
                getLogger().info("mcBtcTrade 500:: 서버 에러");
            } else { // 성공 후 응답 JSON 데이터받기
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line = "";
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                JSONParser parser = new JSONParser();
                Object obj = parser.parse( sb.toString() );

                JSONArray list = (JSONArray)obj;
                JSONObject data1 = (JSONObject)list.get(0);
                double priceData = Double.parseDouble(data1.get("trade_price").toString());

                double lastPrice = priceData;
                coinPrice = lastPrice;
                config.set("btc", coinPrice);
                lastRefreshTime = System.currentTimeMillis();
                config.set("lastRefreshTime", lastRefreshTime);
                saveConfig();
                getLogger().info("mcBtcTrade get data succeed");
            }
        } catch (Exception e) {
            getLogger().info("mcBtcTrade get data fail");
            getLogger().info(e.toString());
        }
    }
}

