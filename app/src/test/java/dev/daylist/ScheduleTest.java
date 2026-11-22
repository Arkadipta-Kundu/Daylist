package dev.daylist;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
import java.time.LocalDate;

public class ScheduleTest {
 @Test public void monthlyLeapThenMarch()throws Exception{
    JSONObject t=new JSONObject().put("repeat","months").put("monthDay",31);
    LocalDate start=LocalDate.parse("2028-01-31"),feb=Reminders.next(t,start,start.plusDays(1));
    assertEquals("2028-02-29",feb.toString());
    assertEquals("2028-03-31",Reminders.next(t,feb,feb.plusDays(1)).toString());
 }
 @Test public void alteredBackupRejected()throws Exception{
    String body="{\"tasks\":[]}";
    JSONObject envelope=new JSONObject().put("format","Daylist").put("version",1).put("payload",body).put("sha256",Snapshot.hash(body));
    assertEquals(0,Snapshot.validate(envelope.toString()).getJSONArray("tasks").length());
    envelope.put("payload",body+" ");
    assertThrows(java.io.IOException.class,()->Snapshot.validate(envelope.toString()));
 }
}
