package whitetac.spigotplugin.mcdogeicoin;

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
        getLogger().info("Enable Mc dogeicoin trader by whitetac");
        if (!setupEconomy() ) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try{
            coinPrice = config.getDouble("dogei");
        }catch(Exception e){
            config.addDefault("dogei", (double) 8000.0);
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
        getLogger().info("Disable Mc dogeicoin trader by whitetac");
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

        if(lastRefreshTime+500*1<System.currentTimeMillis()) {
            //check every 0.5secs
            getPrice();
            if(coinPrice<=0){
                sender.sendMessage(String.format("모든 유저 청산됨."));
                config.set("dogei", 500);
                for (String key : config.getKeys(true)){
                    if(key.equals("btc") || key.equals("dogei")|| key.equals("dogei") || key.equals("lastRefreshTime")) continue;
                    //sender.sendMessage(key);
                    config.set(key, "0,0");
                }
                return true;
            }
        }
        
        Player player = (Player) sender;
        OfflinePlayer offlinePlayer = (OfflinePlayer) sender;
        String uuid = player.getUniqueId().toString();

        //청산


        
        double coinAmount;
        double avgPrice;

        try{
            coinAmount = Double.parseDouble(config.getString(uuid).split(",")[0]);
            avgPrice = Double.parseDouble(config.getString(uuid).split(",")[1]);
        }catch(Exception e){
            sender.sendMessage(String.format("비트코인 지갑이 생성되었습니다."));
            config.addDefault(uuid, (String)"0,0");
            config.set(uuid, (String)"0,0");//자산 매평가
            coinAmount = 0;
            avgPrice = coinPrice;
        }


        
        
        if(command.getLabel().equals("dogei")) {
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
                                double hasCoinAmount = Double.parseDouble(config.getString(uuid).split(",")[0]);
                                double result = hasCoinAmount + amount;
                                avgPrice = ( hasCoinAmount*Double.parseDouble(config.getString(uuid).split(",")[1]) + amount*coinPrice )/(hasCoinAmount+amount);
                                
                                config.set(uuid, result+","+avgPrice);
                                coinAmount = result;
                                moneyAmount = econ.getBalance(offlinePlayer);
                            } else {
                                sender.sendMessage("----------매수 오류----------");
                            }
                        }else{
                            sender.sendMessage("----------잔액부족----------");
                        }
                    break;
                    case "yabuy":
                        amount = Math.round(Double.parseDouble(args[1])*1000.0)/1000.0;
                        if(amount <= moneyAmount){
                            EconomyResponse r = econ.withdrawPlayer(player, amount);
                            if(r.transactionSuccess()) {
                                sender.sendMessage(String.format("----------매수 승인----------%s",amount));
                                double hasCoinAmount = Double.parseDouble(config.getString(uuid).split(",")[0]);
                                double result = hasCoinAmount + amount/coinPrice;
                                avgPrice = ( hasCoinAmount*Double.parseDouble(config.getString(uuid).split(",")[1]) + amount )/(hasCoinAmount+amount);
                                
                                config.set(uuid, result+","+avgPrice);
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
                                double result = Double.parseDouble(config.getString(uuid).split(",")[0]) - amount;
                                if(result == 0){
                                    config.set(uuid, "0,0");
                                    avgPrice = 0;
                                }else{
                                    config.set(uuid, result+","+config.getString(uuid).split(",")[1]);
                                }
                                
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
            }else if(args.length == 1){
                switch(args[0]){
                case "sellall":
                    double amount = coinAmount;
                        if(true){
                            EconomyResponse r = econ.depositPlayer(player, amount*coinPrice);
                            if(r.transactionSuccess()) {
                                sender.sendMessage("----------매도 승인----------");
                                double result = Double.parseDouble(config.getString(uuid).split(",")[0]) - amount;
                                if(result == 0){
                                    config.set(uuid, "0,0");
                                    avgPrice = 0;
                                }else{
                                    config.set(uuid, result+","+config.getString(uuid).split(",")[1]);
                                }
                                
                                coinAmount = result;
                                moneyAmount = econ.getBalance(offlinePlayer);
                            } else {
                                sender.sendMessage("----------매도 오류----------");
                            }
                        }else{
                            sender.sendMessage("----------잔액부족----------");
                        }
                    break;
                    case "buyall":
                        amount = Math.floor(moneyAmount/coinPrice*1000.0)/1000.0;
                        if(amount <= moneyAmount/coinPrice){
                            EconomyResponse r = econ.withdrawPlayer(player, coinPrice*amount);
                            if(r.transactionSuccess()) {
                                sender.sendMessage(String.format("----------매수 승인----------%s",amount));
                                double hasCoinAmount = Double.parseDouble(config.getString(uuid).split(",")[0]);
                                double result = hasCoinAmount + amount;
                                avgPrice = ( hasCoinAmount*Double.parseDouble(config.getString(uuid).split(",")[1]) + amount*coinPrice )/(hasCoinAmount+amount);
                                
                                config.set(uuid, result+","+avgPrice);
                                coinAmount = result;
                                moneyAmount = econ.getBalance(offlinePlayer);
                            } else {
                                sender.sendMessage("----------매수 오류----------");
                            }
                        }else{
                            sender.sendMessage("----------잔액부족----------");
                        }
                }
            }
            sender.sendMessage("------------------------------");
            sender.sendMessage(String.format("인버스 (-100%%부터 청산) 도지코인 시세정보는 업비트api 기반으로 이루어집니다. (최소 수량 0.001)"));
            sender.sendMessage(String.format("거래방법 /dogei buy|sell 수량"));
            sender.sendMessage(String.format(" "));
            sender.sendMessage(String.format("시세 dogei/MD %s", coinPrice));
            sender.sendMessage(String.format("보유 코인 %sdogei", Math.round(coinAmount*1000.0)/1000.0));
            sender.sendMessage(String.format(" "));
            sender.sendMessage(String.format("구매가능 수량 %sdogei (%s)", Math.floor(moneyAmount/coinPrice*1000.0)/1000.0, econ.format(moneyAmount)));
            if(avgPrice>0){
                sender.sendMessage(String.format("평단가 %s", Math.round((avgPrice)*1000.0)/1000.0));
                sender.sendMessage(String.format("보유 코인 가치 %sMD(%s%%)", Math.round((coinAmount*coinPrice)*1000.0)/1000.0, Math.round(((coinPrice/avgPrice-1)*100)*1000.0)/1000.0));
            }else{
                sender.sendMessage(String.format("보유 코인 없음"));
            }
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
            URL url = new URL("https://api.upbit.com/v1/candles/minutes/1?market=KRW-doge&count=1");
    
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
                try{
                    config.getDouble("dogei");
                }catch(Exception e){
                    config.set("dogei", 500);
                    config.set("dogei", coinPrice);
                }
                config.set("dogei", config.getDouble("dogei")*(1+ ((coinPrice/config.getDouble("dogei")-1)*-1) ));
                config.set("dogei", coinPrice);
                coinPrice = config.getDouble("dogei");
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

