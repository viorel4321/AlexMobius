package ai.CustomBoss;

import ai.AbstractNpcAI;

import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.util.Broadcast;

import java.util.Calendar;

public class NewAntharas extends AbstractNpcAI {

    private int ANTHARAS_ID = 40080;//id босса
    private Location ANTHARAS_SPAWN_LOC = new Location(2373, 236009, -3392);//кординаты спавна
    private String message = "Антарас пробудится через 30 минут!";
    private String message2 = "Антарас пробудился!";



    //все, что происходит в этом конструкторе класса EpicRespawn() - происходит во время запуска сервера
    //(но возможно, что есть встроенная механика перезагрузки класса и тогда при перезагрузке класса - тоже, но пока мы делаем так, как если бы только при запуске сервера)
    private NewAntharas() {
        //получаем текущее время
        Calendar currentTime = Calendar.getInstance();
        //получаем время, в которое должен быть заспавнен босс
        Calendar bossTime = Calendar.getInstance();
        bossTime.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
        bossTime.set(Calendar.HOUR_OF_DAY, 14);
        bossTime.set(Calendar.MINUTE, 50);
        bossTime.set(Calendar.SECOND, 0);
        bossTime.set(Calendar.MILLISECOND, 0);
        //получаем время, в которое должен появиться анонс
        Calendar annonceTime = Calendar.getInstance();
        annonceTime.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
        annonceTime.set(Calendar.HOUR_OF_DAY,14);
        annonceTime.set(Calendar.MINUTE, 45);
        annonceTime.set(Calendar.SECOND, 0);
        annonceTime.set(Calendar.MILLISECOND, 0);


        //проверяем разницу во времени
        if (currentTime.getTimeInMillis() < annonceTime.getTimeInMillis()) {
            //если время респа еще не пришло, создаем запланированную задачу через время разницы, чтобы появился анонс
            startQuestTimer("announcement_Antharas", annonceTime.getTimeInMillis() - currentTime.getTimeInMillis(), null, null);
        }
        if (currentTime.getTimeInMillis() < bossTime.getTimeInMillis()) {
            //если время респа еще не пришло, создаем запланированную задачу через время разницы, чтобы появился босс
            startQuestTimer("respawn_antharas", bossTime.getTimeInMillis() - currentTime.getTimeInMillis(), null, null);
        } //else {
          ////  //в противном случае время уже наступило - и босс должен быть заспавнен сразу, вызываем задачу почти сразу (даем секунду на прогрузку класса)
          // startQuestTimer("respawn_antharas", 1000, null, null);
          //}
    }


    @Override
    public String onAdvEvent(String event, Npc npc, Player player) {
        //проверяем, чтобы вызов ивента был именно скриптом, а не игроком
        //final Skill skill = getRandomSkill(npc).getSkill();
        switch (event) {
            case "announcement_Antharas": {
                if (event.equalsIgnoreCase("announcement_Antharas")) {
                    Broadcast.toAllOnlinePlayers(message);
                    Broadcast.toAllOnlinePlayersOnScreen(message);
                }
                break;
            }
            case "respawn_antharas": {
                if (event.equalsIgnoreCase("respawn_antharas")) {
                    addSpawn(ANTHARAS_ID, ANTHARAS_SPAWN_LOC, false, 0, false);
                    Broadcast.toAllOnlinePlayers(message2);
                    Broadcast.toAllOnlinePlayersOnScreen(message2);
                }
                break;
            }
        }
        return super.onAdvEvent(event, npc, player);
    }


    public static void main(String[] args) {
         new NewAntharas();
    }
}
