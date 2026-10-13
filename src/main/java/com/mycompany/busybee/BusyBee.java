package com.mycompany.busybee;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

//===DISCORD BOT ANATOMY 101===
/*
- This code can be thought of as Busy Bee's "mind". The account you see on discord is basically her "body". 
- The two are connected through an API token, a code you should NEVER share with ANYONE or else they will be able to hijack Busy Bee's body.
- If you run two or more instances of this code concurrently, they will attempt to share Busy Bee's body and you will see duplicate responses to commands.
*/

//tip about managing JSON files: most text editor programs will recognize a JSON file and have a "reformat text" option somewhere that will make the file much more human-readable. A file reformatted this way is technically not the same file, but the two are interchangeable as far as Busy Bee is concerned.

public class BusyBee {
    public static JDA api;
    public static Timer reminderTimer = new Timer();
    public static ArrayList<Reminder> reminders = new ArrayList<>();
    public static int remindersIndex = 0;
    public static Timer sleepTimer = new Timer();
    public static Timer wakeTimer = new Timer();
    public static Timer qotdTimer = new Timer();
    public static LocalTime qotdTime = LocalTime.parse("09:00:00");
    public static LocalTime sleepTime = LocalTime.parse("23:00:00");
    public static LocalTime wakeTime = LocalTime.parse("08:00:00");
    public static boolean awake = true;
    public static ArrayList<QotD> qotds = new ArrayList<>();
    public static int qotdIndex = 0;
    public static ArrayList<QotD> qotdMem = new ArrayList<>();
    public static Optional<QotD> currentQuestion = Optional.empty();
    public static Optional<MessageChannelUnion> systemChannel = Optional.empty();
    public static Optional<MessageChannelUnion> qotdChannel = Optional.empty();
    public static boolean verbose = false;
    public static Random rand = new Random();
    public static void main(String[] args) {
        api = JDABuilder.createDefault("REDACTED") //this is Busy Bee's API token I mentioned earlier. On github, this will show up as "REDACTED" because I have removed the actual token for security reasons.
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES)
                .build();
        api.addEventListener(new BBListener());
        
        BusyBee.load(api);
    }
    public static void save(JDA api){ //This function must be called manually by doing the "BB:save" command every time you shut down the bot. Any information given to the bot after you save will not be present the next time she boots up.
        try {
            Path sysPath = Path.of("system.json");
            JSONObject sysObject = new JSONObject();
            if (systemChannel.isPresent()){
                sysObject.put("channel", systemChannel.get().getId());
            }
            if (qotdChannel.isPresent()){
                sysObject.put("qotd channel", qotdChannel.get().getId());
            }
            sysObject.put("wake", wakeTime.toString());
            sysObject.put("sleep", sleepTime.toString());
            sysObject.put("qotd time", qotdTime.toString());
            Files.writeString(sysPath, sysObject.toJSONString());
            Path remsPath = Path.of("reminders.json");
            JSONArray remsArr = new JSONArray();
            for (Reminder rem : reminders){
                remsArr.add(rem.toJSONObj());
            }
            Files.writeString(remsPath, remsArr.toJSONString());
            Path qotdPath = Path.of("qotd.json");
            JSONArray qotdArr = new JSONArray();
            for (QotD q : qotds){
                qotdArr.add(q.toJSONObj());
            }
            Files.writeString(qotdPath, qotdArr.toJSONString());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public static void load(JDA api){ //This function is called automatically upon booting up but there is a split second before Busy Bee "remembers" anything where a user could theoretically use a command and receive a response that indicates her memory is empty. 
        try {
            api.awaitReady();
            Path sysPath = Path.of("system.json");
            if (!Files.readString(sysPath).isEmpty()){ //This line stops Busy Bee from trying to load an empty memory file. If she is rebooted and someone uses a command before her load() function is finished, empty files will replace her old memory. For this reason, it's good to keep backups of her .json files. I back mine up about once a month and generally don't have problems.
                JSONParser sysParser = new JSONParser();
                JSONObject sysObject = (JSONObject)sysParser.parse(Files.readString(sysPath));
                if (sysObject.containsKey("channel")){
                    systemChannel = Optional.of((MessageChannelUnion)api.getGuildChannelById((String)sysObject.get("channel")));
                }
                if (sysObject.containsKey("qotd channel")){
                    qotdChannel = Optional.of((MessageChannelUnion)api.getGuildChannelById((String)sysObject.get("qotd channel")));
                }
                if (sysObject.containsKey("qotd time")){
                    qotdTime = LocalTime.parse((String)sysObject.get("qotd time"));
                }
                else {
                    qotdTime = LocalTime.parse("09:00:00");
                }
                if (LocalTime.now().isBefore(qotdTime)){
                    qotdTimer.scheduleAtFixedRate(new QotDTask(), LocalTime.now().until(BusyBee.qotdTime, ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                }
                else {
                    qotdTimer.scheduleAtFixedRate(new QotDTask(), LocalDateTime.now().until(LocalDateTime.parse(LocalDate.now().plusDays(1).toString()+"T"+BusyBee.qotdTime.toString()), ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                }
                if (sysObject.containsKey("wake")){
                    wakeTime = LocalTime.parse((String)sysObject.get("wake"));
                }
                else {
                    wakeTime = LocalTime.parse("08:00:00");
                }
                if (LocalTime.now().isBefore(wakeTime)){
                    wakeTimer.scheduleAtFixedRate(new WakeTask(), LocalTime.now().until(BusyBee.wakeTime, ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                }
                else {
                    wakeTimer.scheduleAtFixedRate(new WakeTask(), LocalDateTime.now().until(LocalDateTime.parse(LocalDate.now().plusDays(1).toString()+"T"+BusyBee.wakeTime.toString()), ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                }
                if (sysObject.containsKey("sleep")){
                    sleepTime = LocalTime.parse((String)sysObject.get("sleep"));
                }
                else {
                    sleepTime = LocalTime.parse("23:00:00");
                }
                if (LocalTime.now().isBefore(sleepTime)){
                    sleepTimer.scheduleAtFixedRate(new SleepTask(), LocalTime.now().until(BusyBee.sleepTime, ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                }
                else {
                    sleepTimer.scheduleAtFixedRate(new SleepTask(), LocalDateTime.now().until(LocalDateTime.parse(LocalDate.now().plusDays(1).toString()+"T"+BusyBee.sleepTime.toString()), ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                }
                if (LocalTime.now().isBefore(sleepTime) | LocalTime.now().isAfter(wakeTime)){
                    awake = true;
                }
                else {
                    awake = false;
                }
            }
            Path remsPath = Path.of("reminders.json");
            if (!Files.readString(remsPath).isEmpty()){
                reminders.clear();
                JSONParser remsParser = new JSONParser();
                JSONArray remsArr = (JSONArray)remsParser.parse(Files.readString(remsPath));
                for (Object obj : remsArr){
                    JSONObject jObj = (JSONObject)obj;
                    String msg = (String)jObj.get("message");
                    int id = ((Long)jObj.get("id")).intValue();
                    MessageChannelUnion channel = (MessageChannelUnion)api.getGuildChannelById((String)jObj.get("channel"));
                    LocalTime start = LocalTime.parse((String)jObj.get("start"));
                    int interval = Integer.valueOf((String)jObj.get("interval"));
                    ReminderTask task = new ReminderTask(msg, channel);
                    if (start.isBefore(LocalTime.now())){
                        LocalTime nTime = start.plusHours(interval);
                        while (nTime.isBefore(LocalTime.now())){
                            nTime = nTime.plusHours(interval);
                        }
                        reminderTimer.scheduleAtFixedRate(task, LocalTime.now().until(nTime, ChronoUnit.MILLIS), (long)interval * 60 * 60 * 1000);
                    }
                    else {
                        reminderTimer.scheduleAtFixedRate(task, LocalTime.now().until(start, ChronoUnit.MILLIS), (long)interval * 60 * 60 * 1000);
                    }
                    reminders.add(new Reminder(msg, id, task, channel, start, interval));
                }
            }
            Path qotdPath = Path.of("qotd.json");
            if (!Files.readString(qotdPath).isEmpty()){
                qotds.clear();
                JSONParser qotdParser = new JSONParser();
                JSONArray qotdArr = (JSONArray)qotdParser.parse(Files.readString(qotdPath));
                for (Object obj : qotdArr){
                    JSONObject jObj = (JSONObject)obj;
                    String question = (String)jObj.get("question");
                    User submitter = (User)api.getUserById((String)jObj.get("submitter"));
                    int id = ((Long)jObj.get("id")).intValue();
                    float weight = ((Double)jObj.get("weight")).floatValue();
                    int timesAsked = ((Long)jObj.get("times asked")).intValue();
                    int timesMulled = ((Long)jObj.get("times mulliganed")).intValue(); //"Mulligan" is a legacy term for rerolling a question. I have MtG brainworms.
                    String bestAnswer = "";
                    if (jObj.containsKey("best answer")){
                        bestAnswer = (String)jObj.get("best answer");
                    }
                    qotds.add(new QotD(question, submitter, id, weight, timesAsked, timesMulled, bestAnswer));
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public static QotD getQuestion(){
        QotD result = getQuestionByWeight(); //Gets a QotD using the weighted process
        if (qotds.size() > 3){ //If there are four or more questions in memory...
            while (qotdMem.contains(result)){ //...and the question has been asked recently...
                result = getQuestionByWeight();  //...try for a new question.
            }
            qotdMem.add(result); //Once you have a question, add it to the memory, then...
            if (qotdMem.size() > qotds.size()/3){ //...if the list of recently asked questions is bigger than the total number of questions divided by three...
                qotdMem.remove(0); //...remove the oldest question in the list.
            }
        }
        return result;
        //The recently asked questions list is maintained at one third the size of the regular questions list so that if a large purge of questions happens, the memory list should still be shorter than the total list.
        //The recently asked questions list is not stored in memory when Busy Bee is rebooted. If you delete a bunch of questions and then experience some kind of error pertaining to questions that don't exist, try rebooting.
    }
    public static QotD getQuestionByWeight(){
        float totalWeight = (float)qotds.stream().mapToDouble(QotD::getWeight).sum(); //Find total weight of all questions
        float roll = rand.nextFloat(totalWeight); //Find a random decimal number from zero to the total weight
        QotD result = qotds.get(0); //The return value of this function defaults to the first question in the list. If there are no questions in the list when the function is called, an error will occur.
        float tally = 0.0f;
        for (QotD q : qotds){ //Run through each question tallying up the total weight. 
            if (roll < tally){
                result = q;
                break; //When the total weight exceeds the random decimal number, return the question it landed on.
            }
            else {
                tally += q.weight;
            }
        }
        return result;
    }
    static MessageEmbed getQotDUI(){
        EmbedBuilder emb = new EmbedBuilder();
        emb.setTitle("Question of the Day dictionary");
        String display = "";
        for (int i = Math.max(0, qotdIndex-5); i < Math.min(qotds.size(), Math.max(10, qotdIndex+5)); i++){
            if (i == qotdIndex){
                display += "**▹";
            }
            display += String.valueOf(qotds.get(i).id)+": "+qotds.get(i).question.substring(0, Math.min(qotds.get(i).question.length(), 41));
            if (qotds.get(i).question.length() > 41){
                display += "...";
            }
            if (i == qotdIndex){
                display += "**";
            }
            display += "\n";
        }
        emb.setDescription(display);
        if (!qotds.isEmpty()){
            emb.addField("Weight", String.valueOf(qotds.get(qotdIndex).weight), false);
        }
        return emb.build();
    }
    static MessageEmbed getReminderUI(){
        EmbedBuilder emb = new EmbedBuilder();
        emb.setTitle("Reminder dictionary");
        String display = "";
        for (int i = Math.max(0, remindersIndex-5); i < Math.min(reminders.size(), Math.max(10, remindersIndex+5)); i++){
            if (i == remindersIndex){
                display += "**▹";
            }
            display += String.valueOf(reminders.get(i).id);
            if (i == remindersIndex){
                display += "**";
            }
            display += "\n";
        }
        emb.setDescription(display);
        if (!reminders.isEmpty()){
            emb.addField("Start time", OffsetDateTime.parse(LocalDate.now().toString()+"T"+reminders.get(remindersIndex).start.toString()+OffsetDateTime.now().getOffset()).atZoneSameInstant(ZoneId.of("UTC")).toLocalTime().toString(), true);
            emb.addField("Interval", String.valueOf(reminders.get(remindersIndex).interval)+" hours", true);
            emb.addField("Text", reminders.get(remindersIndex).msg, false);
        }
        return emb.build();
    }
}

class BBListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event){
        Message message = event.getMessage();
        String content = message.getContentRaw();
        User user = event.getAuthor();
        MessageChannelUnion channel = event.getChannel();
        Optional<Guild> server = Optional.empty();
        if (message.hasGuild()){
            server = Optional.of(message.getGuild());
        }
        if (!user.isBot()){
            if (content.matches("BB:.*")){
                if (BusyBee.systemChannel.isPresent() && BusyBee.verbose){
                    BusyBee.systemChannel.get().sendMessage("["+LocalDateTime.now()+"] User "+user.getName()+" used command "+content+" in channel "+channel.getName()+".").complete();
                }
                if (content.equals("BB:ping")){
                    channel.sendMessage("pong!").complete();
                }
                else if (content.equals("BB:help")){
                    channel.sendMessage("""
                                        Welcome to the Busy Bee help page. Busy Bee is a discord bot programmed by Marcie to help the Beehive community with daily tasks and questions. Any questions about Busy Bee can be directed to sapphiconoclast on discord or tumblr.
                                        
                                        What follows is a list of commands recognized by Busy Bee.
                                        **BB:ping** pings the bot, letting you know if it's up or down.
                                        **BB:help** takes you to this help page.
                                        **BB:remind help** displays the reminder help page.
                                        **BB:qotd help** displays the question of the day help page.
                                        **BB:system setchannel** designates the current channel as Busy Bee's system channel, where messages concerning the bot's status will be posted occasionally.
                                        **BB:awake get** displays if the bot is awake or not awake (see BB:remind help)
                                        **BB:awake set [true/false]** manually sets the state of the bot to awake or not awake.
                                        **BB:save** saves the bot's memory manually so it can be operated upon.
                                        **BB:load** loads the bot's memory manually, for instance after it has been operated upon.
                                        """).complete();
                }
                else if (content.equals("BB:system setchannel")){
                    BusyBee.systemChannel = Optional.of(channel);
                    BusyBee.save(BusyBee.api);
                    channel.sendMessage("Current channel set as Busy Bee's system channel.").complete();
                }
                else if (content.equals("BB:save")){
                    BusyBee.save(BusyBee.api);
                    channel.sendMessage("Memory saved.").complete();
                }
                else if (content.equals("BB:load")){
                    BusyBee.load(BusyBee.api);
                    channel.sendMessage("Memory loaded.").complete();
                }
                else if (content.equals("BB:awake get")){
                    if (BusyBee.awake){
                        channel.sendMessage("Busy Bee is currently awake. Reminders will be sent.").complete();
                    }
                    else {
                        channel.sendMessage("Busy Bee is currently asleep. Reminders will not be sent.").complete();
                    }
                }
                else if (content.equals("BB:awake set true")){
                    if (!BusyBee.awake){
                        BusyBee.awake = true;
                        channel.sendMessage("Busy Bee is now awake. Reminders will be sent.").complete();
                    }
                }
                else if (content.equals("BB:awake set false")){
                    if (BusyBee.awake){
                        BusyBee.awake = false;
                        channel.sendMessage("Busy Bee is now not awake. Reminders will not be sent.").complete();
                    }
                }
                else if (content.startsWith("BB:remind")){
                    if (content.equals("BB:remind help")){
                        channel.sendMessage("""
                                            Welcome to the Reminder help page.
                                            
                                            The following syntax is used to set up a recurring reminder:
                                            > BB:remind setup [message]; [time]; [interval]
                                            "[message]" here is replaced with your message, enclosed in quotation marks.
                                            "[time]" here is replaced with the time of day you would like the reminder to execute, in 24-hour HH:MM format using UTC+0 as the timezone.
                                            "[interval]" here is replaced with the length of time you'd like to elapse between recurrences, in hours.
                                            When a reminder executes, it will post the message in the channel it was set up in. Roles that were @ed in the original message will be @ed again whenever the message is posted.
                                            
                                            As a reminder, noon UTC+0 is <t:1791028800:T> your time.
                                            
                                            __Example__
                                            > BB:remind setup Take your evening pills!; 01:00; 24
                                            Posts "Take your evening pills!" at 8 PM EST every 24 hours
                                            > BB:remind setup Shrimp check!; 14:00; 3
                                            Posts "Shrimp check!" at 9 AM EST and every three hours after that, except during time defined by sleep/wake mode.
                                            
                                            *Marcie note: I have no idea how Daylight Savings Time factors into this. Timer tasks seem to fire based on system time of whatever computer Busy Bee is running on so I have to assume that as long as that's adjusted correctly then everything should work fine but it's possible reminders will get shifted forward or backward an hour when DST starts or ends.*
                                            
                                            Sleep/wake mode can be used to prevent pings during unwanted hours of the night. The following syntax is used to set up a time for Busy Bee to "sleep" (stop posting reminders) and "wake" (start posting them again the next day). 
                                            > BB:remind sleep [time]
                                            > BB:remind wake [time]
                                            "[time]" here is replaced by the time of day to be set, in 24-hour HH:MM format using UTC+0 as the timezone.
                                            
                                            Sleep/wake times default to 4:00 UTC+0 and 12:00 UTC+0 respectively, corresponding to 11 PM and 7 AM EST.
                                            
                                            Additional commands are available:
                                            **BB:remind edit** will pull up a UI where individual reminders can be viewed and edited.
                                            
                                            """).complete();
                    }
                    else if (content.startsWith("BB:remind setup")){ //input is given in UTC+0
                        String reminder = content.substring(16);
                        String[] tokens = reminder.split("; ");
                        int interval = Integer.valueOf(tokens[2]);
                        int id = Integer.valueOf(message.getId().substring(message.getId().length()-5));
                        LocalTime time = OffsetDateTime.parse(LocalDate.now().toString()+"T"+tokens[1]+"+00:00").atZoneSameInstant(ZoneId.systemDefault()).toLocalTime(); //time is stored in local time at an offset determined by your computer's system time.
                        ReminderTask task = new ReminderTask(tokens[0], channel);
                        //the way this scheduling process works is effectively a template for all other scheduling functionality.
                        if (LocalTime.now().isBefore(time)){ //if now is before time, schedule the reminder normal-style
                            BusyBee.reminderTimer.scheduleAtFixedRate(task, LocalDateTime.now().until(LocalDateTime.of(LocalDate.now(), time), ChronoUnit.MILLIS), interval * 60 * 60 * 1000);
                        }
                        else { //if now is after time, schedule the reminder for time plus however many intervals it takes to make it after now
                            LocalDateTime nTime = LocalDateTime.of(LocalDate.now(), time);
                            while (nTime.isBefore(LocalDateTime.now())){
                                nTime = nTime.plusHours(interval);
                            }
                            BusyBee.reminderTimer.scheduleAtFixedRate(task, LocalDateTime.now().until(nTime, ChronoUnit.MILLIS), interval * 60 * 60 * 1000);
                        }
                        BusyBee.reminders.add(new Reminder(tokens[0], id, task, channel, time, Integer.valueOf(tokens[2])));
                        BusyBee.save(BusyBee.api);
                        channel.sendMessage("Reminder with ID "+String.valueOf(id)+" set for <t:"+OffsetDateTime.parse(LocalDate.now().toString()+"T"+tokens[1]+"+00:00").toEpochSecond()+":t> and every "+tokens[2]+" hours thereafter.").complete();
                    }
                    else if (content.equals("BB:remind edit")){
                        channel.sendMessage(new MessageCreateBuilder().addEmbeds(BusyBee.getReminderUI()).addComponents(
                                ActionRow.of(Button.primary("remind/edit/up3", "▲▲▲"), Button.primary("remind/edit/up1", "▲"), Button.primary("remind/edit/down1", "▼"), Button.primary("remind/edit/down3", "▼▼▼")),
                                ActionRow.of(Button.danger("remind/edit/text", "Edit text"), Button.danger("remind/edit/start", "Edit start"), Button.danger("remind/edit/interval", "Edit interval"), Button.danger("remind/edit/delete", "Delete"))
                        ).build()).complete();
                    }
                    else if (content.startsWith("BB:remind wake")){
                        BusyBee.wakeTime = OffsetDateTime.parse(LocalDate.now().toString()+"T"+content.substring(15)+"+00:00").atZoneSameInstant(ZoneId.systemDefault()).toLocalTime();
                        BusyBee.wakeTimer.cancel();
                        BusyBee.wakeTimer = new Timer();
                        if (LocalTime.now().isBefore(BusyBee.wakeTime)){
                            BusyBee.wakeTimer.scheduleAtFixedRate(new WakeTask(), LocalDateTime.now().until(OffsetDateTime.parse(LocalDate.now().toString()+"T"+BusyBee.wakeTime.toString()+"+00:00"), ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                        }
                        else {
                            BusyBee.wakeTimer.scheduleAtFixedRate(new WakeTask(), LocalDateTime.now().until(OffsetDateTime.parse(LocalDate.now().plusDays(1).toString()+"T"+BusyBee.wakeTime.toString()+"+00:00"), ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                        }
                        BusyBee.save(BusyBee.api);
                        channel.sendMessage("Wake time set for <t:"+OffsetDateTime.parse(LocalDate.now().toString()+"T"+content.substring(15)+"+00:00").toEpochSecond()+":t>").complete();
                    }
                    else if (content.startsWith("BB:remind sleep")){
                        BusyBee.sleepTime = OffsetDateTime.parse(LocalDate.now().toString()+"T"+content.substring(16)+"+00:00").atZoneSameInstant(ZoneId.systemDefault()).toLocalTime();
                        BusyBee.sleepTimer.cancel();
                        BusyBee.sleepTimer = new Timer();
                        if (LocalTime.now().isBefore(BusyBee.sleepTime)){
                            BusyBee.sleepTimer.scheduleAtFixedRate(new SleepTask(), LocalDateTime.now().until(OffsetDateTime.parse(LocalDate.now().toString()+"T"+BusyBee.sleepTime.toString()+"+00:00"), ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                        }
                        else {
                            BusyBee.sleepTimer.scheduleAtFixedRate(new SleepTask(), LocalDateTime.now().until(OffsetDateTime.parse(LocalDate.now().plusDays(1).toString()+"T"+BusyBee.sleepTime.toString()+"+00:00"), ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                        }
                        BusyBee.save(BusyBee.api);
                        channel.sendMessage("Sleep time set for <t:"+OffsetDateTime.parse(LocalDate.now().toString()+"T"+content.substring(16)+"+00:00").toEpochSecond()+":t>").complete();
                    }
                }
                else if (content.startsWith("BB:qotd")){
                    if (content.equals("BB:qotd help")){
                        channel.sendMessage("""
                                            Welcome to the Question of the Day help page.
                                            
                                            The following commands are available:
                                            **BB:qotd schedule [time]** schedules the bot to post the Question of the Day at the given time. Time should be input in 24-hour HH:MM format using UTC+0 as the timezone.
                                            **BB:qotd setchannel** sets the current channel as the QotD channel.
                                            **BB:qotd submit [question]** adds a question to the bot's memory.
                                            **BB:qotd edit** pulls up a UI that lets you edit questions.
                                            **BB:qotd upload** with a .txt file attached, this command allows you to submit multiple questions seperated by line breaks. Example .txt file:
                                            ```
                                            Who's on first?
                                            Orange you glad I didn't say banana?
                                            What is the meaning of life, the universe, and everything?
                                            ```
                                            would upload three questions.
                                            
                                            """).complete();
                    }
                    else if (content.startsWith("BB:qotd schedule")){
                        BusyBee.qotdTime = OffsetDateTime.parse(LocalDate.now().toString()+"T"+content.substring(17)+"+00:00").atZoneSameInstant(ZoneId.systemDefault()).toLocalTime();
                        BusyBee.qotdTimer.cancel();
                        BusyBee.qotdTimer = new Timer();
                        if (BusyBee.qotdTime.isAfter(LocalTime.now())){
                            BusyBee.qotdTimer.scheduleAtFixedRate(new QotDTask(), LocalTime.now().until(BusyBee.qotdTime, ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                        }
                        else {
                            BusyBee.qotdTimer.scheduleAtFixedRate(new QotDTask(), LocalDateTime.now().until(LocalDateTime.of(LocalDate.now().plusDays(1), BusyBee.qotdTime), ChronoUnit.MILLIS), 24 * 60 * 60 * 1000);
                        }
                        BusyBee.save(BusyBee.api);
                        channel.sendMessage("QotD time set for <t:"+OffsetDateTime.parse(LocalDate.now().toString()+"T"+content.substring(17)+"+00:00").atZoneSameInstant(ZoneId.systemDefault()).toEpochSecond()+":t>").complete();
                    }
                    else if (content.equals("BB:qotd setchannel")){
                        BusyBee.qotdChannel = Optional.of(channel);
                        BusyBee.save(BusyBee.api);
                        channel.sendMessage("Channel designated as Question of the Day channel.").complete();
                    }
                    else if (content.startsWith("BB:qotd submit")){
                        int id = Integer.valueOf(message.getId().substring(message.getId().length()-5));
                        BusyBee.qotds.add(new QotD(content.substring(15), user, id));
                        BusyBee.save(BusyBee.api);
                        channel.sendMessage("Question added with ID "+String.valueOf(id)+".").complete();
                    }
                    else if (content.equals("BB:qotd edit")){
                        channel.sendMessage(new MessageCreateBuilder().addEmbeds(BusyBee.getQotDUI()).addComponents(
                                ActionRow.of(Button.primary("qotd/edit/up3", "▲▲▲"), Button.primary("qotd/edit/up1", "▲"), Button.primary("qotd/edit/down1", "▼"), Button.primary("qotd/edit/down3", "▼▼▼")),
                                ActionRow.of(Button.danger("qotd/edit/text", "Edit text"), Button.danger("qotd/edit/weight", "Edit weight"), Button.danger("qotd/edit/delete", "Delete"))
                        ).build()).complete();
                    }
                    else if (content.equals("BB:qotd upload")){
                        if (event.getMessage().getAttachments().isEmpty()){
                            channel.sendMessage("You must upload a .txt file attached to that message.").complete();
                        }
                        else if (!event.getMessage().getAttachments().get(0).getFileExtension().equals("txt")){
                            channel.sendMessage("You must upload a .txt file attached to that message.").complete();
                        }
                        else {
                            event.getMessage().getAttachments().get(0).getProxy().downloadToFile(new File("temp.txt")).thenAccept(f -> {
                                try {
                                    int id = Integer.valueOf(message.getId().substring(message.getId().length()-5));
                                    int count = 0;
                                    List<String> questions = Files.readAllLines(f.toPath());
                                    System.out.println(questions);
                                    for (String q : questions) {
                                        BusyBee.qotds.add(new QotD(q, user, id));
                                        id++;
                                        count++;
                                    }
                                    channel.sendMessage(String.valueOf(count)+" questions added.").complete();
                                }
                                catch (Exception ex) {
                                    channel.sendMessage("Error: "+ex.toString()).complete();
                                }
                            });
                        }
                    }
                }
            }
        }
    }
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event){
        Message message = event.getMessage();
        User clicker = event.getUser();
        MessageChannelUnion channel = event.getChannel();
        Optional<Guild> server = Optional.empty();
        String buttonId = event.getCustomId();
        String[] tokens = buttonId.split("/");
        if (message.hasGuild()){
            server = Optional.of(event.getGuild());
        }
        if (message.getAuthor().getId().equals("1555991331786989688")){
            if (tokens[0].equals("qotd")){
                if (tokens[1].equals("mulligan")){
                    BusyBee.currentQuestion.get().votesToMull++;
                    if (BusyBee.currentQuestion.get().votesToMull < 3){
                        event.reply("Votes to reroll: "+String.valueOf(BusyBee.currentQuestion.get().votesToMull)+"/3").complete();
                    }
                    else {
                        BusyBee.currentQuestion.get().votesToMull = 0;
                        BusyBee.currentQuestion.get().timesMulled++;
                        event.reply("Question rerolled.").complete();
                        BusyBee.qotdTimer.schedule(new QotDTask(), 7);
                    }
                }
                else if (tokens[1].equals("edit")){
                    if (tokens[2].equals("delete")){
                        BusyBee.qotds.remove(BusyBee.qotds.get(BusyBee.qotdIndex));
                        BusyBee.save(BusyBee.api);
                        event.reply("QotD removed.").complete();
                    }
                    else if (tokens[2].equals("weight")){
                        TextInput weight = TextInput.create("weight", TextInputStyle.SHORT)
                                .setPlaceholder("The likelihood of this question appearing. Default 1.0")
                                .build();
                        Modal modal = Modal.create("qotd/edit/weight/"+String.valueOf(BusyBee.qotds.get(BusyBee.qotdIndex).id), "Editing QotD with ID "+String.valueOf(BusyBee.qotds.get(BusyBee.qotdIndex).id))
                                .addComponents(Label.of("New weight:", weight))
                                .build();
                        event.replyModal(modal).complete();
                    }
                    else if (tokens[2].equals("text")){
                        TextInput text = TextInput.create("text", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("The text of this question")
                                .build();
                        Modal modal = Modal.create("qotd/edit/text/"+String.valueOf(BusyBee.qotds.get(BusyBee.qotdIndex).id), "Editing QotD with ID "+String.valueOf(BusyBee.qotds.get(BusyBee.qotdIndex).id))
                                .addComponents(Label.of("New text:", text))
                                .build();
                        event.replyModal(modal).complete();
                    }
                    else {
                        if (tokens[2].equals("up3")){
                            BusyBee.qotdIndex = Math.max(0, BusyBee.qotdIndex-3);
                        }
                        else if (tokens[2].equals("up1")){
                            BusyBee.qotdIndex = Math.max(0, BusyBee.qotdIndex-1);
                        }
                        else if (tokens[2].equals("down1")){
                            BusyBee.qotdIndex = Math.min(BusyBee.qotds.size()-1, BusyBee.qotdIndex+1);
                        }
                        else if (tokens[2].equals("down3")){
                            BusyBee.qotdIndex = Math.min(BusyBee.qotds.size()-1, BusyBee.qotdIndex+3);
                        }
                        event.editMessage(new MessageEditBuilder().setEmbeds(BusyBee.getQotDUI()).build()).complete();
                    }
                }
            }
            else if (tokens[0].equals("remind")){
                if (tokens[1].equals("edit")){
                    if (tokens[2].equals("delete")){
                        Reminder reminder = BusyBee.reminders.get(BusyBee.remindersIndex);
                        ReminderTask remTask = reminder.task;
                        remTask.cancel();
                        BusyBee.reminders.remove(reminder);
                        BusyBee.save(BusyBee.api);
                        event.reply("Reminder canceled.").complete();
                    }
                    else if (tokens[2].equals("start")){
                        TextInput time = TextInput.create("start", TextInputStyle.SHORT)
                                .setPlaceholder("The time, in 24-hour HH:MM UTC+0 format, that this reminder starts at.")
                                .build();
                        Modal modal = Modal.create("reminder/edit/start/"+String.valueOf(BusyBee.reminders.get(BusyBee.remindersIndex).id), "Editing Reminder with ID "+String.valueOf(BusyBee.reminders.get(BusyBee.remindersIndex).id))
                                .addComponents(Label.of("New start time:", time))
                                .build();
                        event.replyModal(modal).complete();
                    }
                    else if (tokens[2].equals("text")){
                        TextInput text = TextInput.create("text", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("The text of this reminder.")
                                .build();
                        Modal modal = Modal.create("reminder/edit/text/"+String.valueOf(BusyBee.reminders.get(BusyBee.remindersIndex).id), "Editing Reminder with ID "+String.valueOf(BusyBee.reminders.get(BusyBee.remindersIndex).id))
                                .addComponents(Label.of("New text:", text))
                                .build();
                        event.replyModal(modal).complete();
                    }
                    else if (tokens[2].equals("interval")){
                        TextInput interval = TextInput.create("interval", TextInputStyle.SHORT)
                                .setPlaceholder("The interval at which this reminder executes, in hours.")
                                .build();
                        Modal modal = Modal.create("reminder/edit/interval/"+String.valueOf(BusyBee.reminders.get(BusyBee.remindersIndex).id), "Editing Reminder with ID "+String.valueOf(BusyBee.reminders.get(BusyBee.remindersIndex).id))
                                .addComponents(Label.of("New interval:", interval))
                                .build();
                        event.replyModal(modal).complete();
                    }
                    else {
                        if (tokens[2].equals("up3")){
                            BusyBee.qotdIndex = Math.max(0, BusyBee.qotdIndex-3);
                        }
                        else if (tokens[2].equals("up1")){
                            BusyBee.qotdIndex = Math.max(0, BusyBee.qotdIndex-1);
                        }
                        else if (tokens[2].equals("down1")){
                            BusyBee.qotdIndex = Math.min(BusyBee.qotds.size()-1, BusyBee.qotdIndex+1);
                        }
                        else if (tokens[2].equals("down3")){
                            BusyBee.qotdIndex = Math.min(BusyBee.qotds.size()-1, BusyBee.qotdIndex+3);
                        }
                        event.editMessage(new MessageEditBuilder().setEmbeds(BusyBee.getQotDUI()).build()).complete();
                    }
                }
            }
        }
    }
    @Override
    public void onModalInteraction(ModalInteractionEvent event){
        if (event.getModalId().startsWith("qotd/edit")){
            if (event.getModalId().contains("text")){
                String qotdId = event.getModalId().split("/")[3];
                QotD qotd = BusyBee.qotds.stream().filter(q -> q.id == Integer.valueOf(qotdId)).findFirst().get();
                qotd.question = event.getValue("text").getAsString();
                BusyBee.save(BusyBee.api);
                event.reply("Question edited.").complete();
            }
            else if (event.getModalId().contains("weight")) {
                String qotdId = event.getModalId().split("/")[3];
                QotD qotd = BusyBee.qotds.stream().filter(q -> q.id == Integer.valueOf(qotdId)).findFirst().get();
                qotd.weight = Float.valueOf(event.getValue("weight").getAsString());
                BusyBee.save(BusyBee.api);
                event.reply("Question edited.").complete();
            }
            event.getMessage().editMessage(new MessageEditBuilder().setEmbeds(BusyBee.getQotDUI()).build()).complete();
        }
        else if (event.getModalId().startsWith("reminder/edit")){
            if (event.getModalId().contains("text")){
                String remId = event.getModalId().split("/")[3];
                Reminder rem = BusyBee.reminders.stream().filter(r -> r.id == Integer.valueOf(remId)).findFirst().get();
                rem.msg = event.getValue("text").getAsString();
                rem.task.msg = event.getValue("text").getAsString();
                BusyBee.save(BusyBee.api);
                event.reply("Reminder edited.").complete();
            }
            else if (event.getModalId().contains("start")) {
                String remId = event.getModalId().split("/")[3];
                Reminder rem = BusyBee.reminders.stream().filter(r -> r.id == Integer.valueOf(remId)).findFirst().get();
                rem.task.cancel();
                rem.task = new ReminderTask(rem.task.msg, rem.task.channel);
                LocalTime start = LocalTime.parse(event.getValue("start").getAsString());
                if (start.isBefore(LocalTime.now())){
                    LocalTime nTime = start.plusHours(rem.interval);
                    while (nTime.isBefore(LocalTime.now())){
                        nTime = nTime.plusHours(rem.interval);
                    }
                    BusyBee.reminderTimer.scheduleAtFixedRate(rem.task, LocalTime.now().until(nTime, ChronoUnit.MILLIS), (long)rem.interval * 60 * 60 * 1000);
                }
                else {
                    BusyBee.reminderTimer.scheduleAtFixedRate(rem.task, LocalTime.now().until(start, ChronoUnit.MILLIS), (long)rem.interval * 60 * 60 * 1000);
                }
                BusyBee.reminders.add(new Reminder(rem.msg, rem.id, rem.task, rem.channel, start, rem.interval));
                BusyBee.reminders.remove(rem);
                BusyBee.save(BusyBee.api);
                event.reply("Reminder edited.").complete();
            }
            else if (event.getModalId().contains("interval")) {
                String remId = event.getModalId().split("/")[3];
                Reminder rem = BusyBee.reminders.stream().filter(r -> r.id == Integer.valueOf(remId)).findFirst().get();
                rem.task.cancel();
                rem.task = new ReminderTask(rem.task.msg, rem.task.channel);
                int interval = Integer.valueOf(event.getValue("interval").getAsString());
                if (rem.start.isBefore(LocalTime.now())){
                    LocalTime nTime = rem.start.plusHours(interval);
                    while (nTime.isBefore(LocalTime.now())){
                        nTime = nTime.plusHours(interval);
                    }
                    BusyBee.reminderTimer.scheduleAtFixedRate(rem.task, LocalTime.now().until(nTime, ChronoUnit.MILLIS), (long)interval * 60 * 60 * 1000);
                }
                else {
                    BusyBee.reminderTimer.scheduleAtFixedRate(rem.task, LocalTime.now().until(rem.start, ChronoUnit.MILLIS), (long)interval * 60 * 60 * 1000);
                }
                BusyBee.reminders.add(new Reminder(rem.msg, rem.id, rem.task, rem.channel, rem.start, interval));
                BusyBee.reminders.remove(rem);
                BusyBee.save(BusyBee.api);
                event.reply("Reminder edited.").complete();
            }
            event.getMessage().editMessage(new MessageEditBuilder().setEmbeds(BusyBee.getReminderUI()).build()).complete();
        }
    }
    /* Defunct "best answer" functionality.
    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event){
        if (event.getChannel().equals(BusyBee.qotdChannel.get()) && event.getReaction().getEmoji().equals(Emoji.fromFormatted("🥇"))){
            if (BusyBee.currentQuestion.isPresent()){
                if (event.getReaction().getCount() > 2){
                    BusyBee.currentQuestion.get().bestAnswer = Optional.of(event.getJumpUrl());
                    BusyBee.save(BusyBee.api);
                    event.getChannel().sendMessage("New best answer chosen! "+event.getJumpUrl()).complete();
                }
            }
        }
    }
    */
}

class QotD {
    QotD(String question, User submitter, int id){
        this.question = question;
        this.submitter = submitter;
        this.id = id;
        this.weight = 1.0f;
        this.timesAsked = 0;
        this.timesMulled = 0;
    }
    QotD(String question, User submitter, int id, float weight, int timesAsked, int timesMulled, String bestAnswer){
        this.question = question;
        this.submitter = submitter;
        this.id = id;
        this.weight = weight;
        this.timesAsked = timesAsked;
        this.timesMulled = timesMulled;
        if (!bestAnswer.isEmpty()){
            this.bestAnswer = Optional.of(bestAnswer);
        }
    }
    String question = "Why?";
    User submitter = BusyBee.api.getSelfUser();
    int id = 0;
    float weight = 1.0f;
    int timesAsked = 0;
    int timesMulled = 0;
    int votesToMull = 0;
    Optional<String> bestAnswer = Optional.empty();
    JSONObject toJSONObj(){
        JSONObject obj = new JSONObject();
        obj.put("question", question);
        obj.put("submitter", submitter.getId());
        obj.put("id", id);
        obj.put("weight", weight);
        obj.put("times asked", timesAsked);
        obj.put("times mulliganed", timesMulled);
        /*if (bestAnswer.isPresent()){
            obj.put("best answer", bestAnswer.get());
        }
        else {
            obj.put("best answer", "");
        }*/
        return obj;
    }
    double getWeight(){
        return (double)weight;
    }
}

class QotDTask extends TimerTask {
    @Override
    public void run(){
        if (BusyBee.qotdChannel.isPresent() && !BusyBee.qotds.isEmpty()){
            QotD q = BusyBee.getQuestion();
            BusyBee.currentQuestion = Optional.of(q);
            q.timesAsked++;
            EmbedBuilder em = new EmbedBuilder();
            em.setTitle("Question of the Day "+LocalDate.now().toString());
            em.setDescription(q.question);
            em.setAuthor("Submitted by "+q.submitter.getName()+".");
            /*if (q.bestAnswer.isPresent()){
                em.setFooter("Times asked: "+String.valueOf(q.timesAsked)+" | Times rerolled: "+String.valueOf(q.timesMulled)+"\nBest answer: "+q.bestAnswer.get());
            }
            else {
                em.setFooter("Times asked: "+String.valueOf(q.timesAsked)+" | Times rerolled: "+String.valueOf(q.timesMulled)+"\nOptionally, award best answer with 🥇 reacts");
            }*/
            em.setFooter("Times asked: "+String.valueOf(q.timesAsked)+" | Times rerolled: "+String.valueOf(q.timesMulled));
            MessageCreateBuilder messageBuilder = new MessageCreateBuilder().addEmbeds(em.build()).addComponents(ActionRow.of(Button.danger("qotd/mulligan", "Reroll")));
            if (!BusyBee.qotdChannel.get().asGuildMessageChannel().getGuild().getRolesByName("QotD", true).isEmpty()){
                messageBuilder.setContent(BusyBee.qotdChannel.get().asGuildMessageChannel().getGuild().getRolesByName("QotD", true).get(0).getAsMention());
            }
            BusyBee.qotdChannel.get().sendMessage(messageBuilder.build()).complete();
        }
        else {
            System.out.println("No QotD posted, either no QotD channel exists or the QotD list is empty.");
        }
    }
}

class Reminder {
    Reminder(String msg, int id, ReminderTask task, MessageChannelUnion channel, LocalTime start, int interval){
        this.msg = msg;
        this.id = id;
        this.channel = channel;
        this.start = start;
        this.interval = interval;
        this.task = task;
    }
    String msg;
    int id = 0;
    MessageChannelUnion channel;
    LocalTime start;
    int interval;
    ReminderTask task;
    JSONObject toJSONObj(){
        JSONObject obj = new JSONObject();
        obj.put("message", msg);
        obj.put("id", id);
        obj.put("channel", channel.getId());
        obj.put("start", start.toString());
        obj.put("interval", String.valueOf(interval));
        return obj;
    }
}

class ReminderTask extends TimerTask {
    ReminderTask(String msg, MessageChannelUnion channel){
        this.msg = msg;
        this.channel = channel;
    }
    String msg;
    MessageChannelUnion channel;
    @Override
    public void run(){
        if (BusyBee.awake){
            channel.sendMessage(msg).complete();
        }
    }
}

class SleepTask extends TimerTask {
    @Override
    public void run(){
        BusyBee.awake = false;
        if (BusyBee.systemChannel.isPresent()){
            BusyBee.systemChannel.get().sendMessage("Good night, Beehive!").complete();
        }
    }
}

class WakeTask extends TimerTask {
    @Override
    public void run(){
        BusyBee.awake = true;
        if (BusyBee.systemChannel.isPresent()){
            BusyBee.systemChannel.get().sendMessage("Good morning, Beehive!").complete();
        }
    }
}