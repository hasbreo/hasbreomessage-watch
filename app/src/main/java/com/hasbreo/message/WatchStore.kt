package com.hasbreo.message

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object WatchStore{
 fun account(context:Context):String?=Vault(context).get("account")
 fun snapshot(context:Context):JSONObject?=Vault(context).get("snapshot")?.let{JSONObject(it)}
 fun messages(context:Context,room:String):JSONObject?=Vault(context).get("room:$room")?.let{JSONObject(it)}
 @Synchronized fun accept(context:Context,packet:JSONObject){
  val result=packet.optJSONObject("result")?:return;val v=Vault(context)
  if(result.has("rooms")){
   val owner=result.getString("userId");if(account(context)!=null&&account(context)!=owner)clear(context)
   v.put("account",owner);v.put("snapshot",result.put("cachedAt",System.currentTimeMillis()).toString())
  }
  if(result.has("messages")){
   val room=result.getString("roomId");v.put("room:$room",result.put("cachedAt",System.currentTimeMillis()).toString())
   val rooms=JSONArray(v.get("cachedRooms")?:"[]");if(!(0 until rooms.length()).any{rooms.optString(it)==room})rooms.put(room)
   while(rooms.length()>30){v.put("room:"+rooms.remove(0),null)};v.put("cachedRooms",rooms.toString())
  }
  if(result.optBoolean("sent"))removeQueued(context,packet.optString("requestId"))
 }
 @Synchronized fun queue(context:Context,request:JSONObject){val list=queued(context);if((0 until list.length()).any{list.getJSONObject(it).getString("requestId")==request.getString("requestId")})return;require(list.length()<20){"Your watch outbox is full. Connect your phone or discard a queued reply."};list.put(request);Vault(context).put("outbox",list.toString())}
 fun queued(context:Context)=JSONArray(Vault(context).get("outbox")?:"[]")
 @Synchronized fun removeQueued(context:Context,id:String){val list=queued(context);val next=JSONArray();for(i in 0 until list.length()){val r=list.getJSONObject(i);if(r.optString("requestId")!=id)next.put(r)};Vault(context).put("outbox",next.toString())}
 fun clear(context:Context){val v=Vault(context);val rooms=JSONArray(v.get("cachedRooms")?:"[]");for(i in 0 until rooms.length())v.put("room:"+rooms.optString(i),null);for(key in listOf("cachedRooms","snapshot","account","outbox"))v.put(key,null)}
}
