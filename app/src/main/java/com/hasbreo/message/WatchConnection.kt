package com.hasbreo.message
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object WatchConnection{
 private val callbacks=ConcurrentHashMap<String,(JSONObject?,String?)->Unit>()
 private val pending=ConcurrentHashMap.newKeySet<String>()
 fun request(context:Context,action:String,room:String?=null,data:JSONObject=JSONObject(),done:(JSONObject?,String?)->Unit={_,_->}){
  val id=UUID.randomUUID().toString();val request=data.put("requestId",id).put("action",action).put("account",WatchStore.account(context)?:"")
  if(room!=null)request.put("roomId",room)
  if(action=="disconnect")Vault(context).put("pendingDisconnect",request.toString())
  if(action=="send")try{WatchStore.queue(context,request)}catch(e:Exception){done(null,e.message);return}
  callbacks[id]=done;send(context,request)
 }
 private fun send(context:Context,request:JSONObject){
  val id=request.getString("requestId");if(!pending.add(id))return
  Wearable.getCapabilityClient(context).getCapability("hasbreo_message_phone",CapabilityClient.FILTER_REACHABLE).addOnSuccessListener{capability->
   val node=capability.nodes.sortedByDescending{it.isNearby}.firstOrNull()
   if(node==null){finish(id,null,"Phone disconnected. Your reply stays on the watch until you reconnect and retry.");return@addOnSuccessListener}
   Wearable.getMessageClient(context).sendMessage(node.id,"/hasbreo/request",request.toString().toByteArray(Charsets.UTF_8)).addOnFailureListener{finish(id,null,"Could not reach your phone. Reconnect and retry.")}
   Handler(Looper.getMainLooper()).postDelayed({if(pending.remove(id))callbacks.remove(id)?.invoke(null,"Waiting for your phone. Check the chat before retrying.")},65000)
  }.addOnFailureListener{finish(id,null,"Open Hasbreo on your phone and connect your watch in Settings.")}
 }
 fun retry(context:Context){Vault(context).get("pendingDisconnect")?.let{send(context,JSONObject(it))};val list=WatchStore.queued(context);for(i in 0 until list.length()){val request=list.getJSONObject(i);if(ReplyPolicy.maySend(request.optString("text"),request.optString("account"),WatchStore.account(context)))send(context,request)}}
 fun receive(context:Context,packet:JSONObject){
  if(packet.optJSONObject("result")?.optBoolean("disconnected")==true)Vault(context).put("pendingDisconnect",null)
  val id=packet.optString("requestId");WatchStore.accept(context,packet);finish(id,packet.optJSONObject("result"),packet.optString("error").takeIf{it.isNotBlank()})
 }
 private fun finish(id:String,result:JSONObject?,error:String?){pending.remove(id);Handler(Looper.getMainLooper()).post{callbacks.remove(id)?.invoke(result,error)}}
}
class WatchListener:WearableListenerService(){
 override fun onMessageReceived(event:MessageEvent){if(event.data.size>100000)return
  when(event.path){
   "/hasbreo/changed"->{sendBroadcast(Intent("com.hasbreo.message.CHANGED").setPackage(packageName).putExtra("refresh",true))}
   "/hasbreo/clear"->{WatchStore.clear(this);sendBroadcast(Intent("com.hasbreo.message.CHANGED").setPackage(packageName).putExtra("cleared",true))}
   "/hasbreo/connected"->{Vault(this).put("pendingDisconnect",null);WatchConnection.request(this,"snapshot");WatchConnection.retry(this);sendBroadcast(Intent("com.hasbreo.message.CHANGED").setPackage(packageName))}
   "/hasbreo/response"->{val packet=try{JSONObject(String(event.data,Charsets.UTF_8))}catch(_:Exception){return};WatchConnection.receive(this,packet);sendBroadcast(Intent("com.hasbreo.message.CHANGED").setPackage(packageName).putExtra("packet",packet.toString()))}
  }
 }
}
