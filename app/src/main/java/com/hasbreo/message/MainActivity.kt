package com.hasbreo.message

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class MainActivity:ComponentActivity(){
 private val bg=Color.rgb(40,46,51);private val panel=Color.rgb(61,70,78);private val sage=Color.rgb(189,216,197);private val white=Color.rgb(244,246,248);private val muted=Color.rgb(194,204,211)
 private lateinit var scroll:ScrollView;private lateinit var content:LinearLayout
 private var roomId:String?=null;private var roomName="Conversation";private var status="";private var busy=false;private var compose=false
 private val voice=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->if(r.resultCode==RESULT_OK){val text=r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull();if(!text.isNullOrBlank())editor(text)}}
 private val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(intent.getBooleanExtra("cleared",false)){roomId=null;compose=false;status="Signed out on phone."};if(!compose){if(intent.getBooleanExtra("refresh",false))refresh()else render()}}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=bg;window.navigationBarColor=bg
  scroll=ScrollView(this).apply{setBackgroundColor(bg);isFillViewport=true;clipToPadding=false};content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(22),dp(28),dp(22),dp(40))};scroll.addView(content,ViewGroup.LayoutParams(-1,-2));setContentView(scroll)
  ContextCompat.registerReceiver(this,receiver,IntentFilter("com.hasbreo.message.CHANGED"),ContextCompat.RECEIVER_NOT_EXPORTED)
  roomId=savedInstanceState?.getString("room");roomName=savedInstanceState?.getString("name")?:roomName
  onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){if(compose){compose=false;render()}else if(roomId!=null){roomId=null;render()}else finish()}})
  render();refresh()
 }
 override fun onResume(){super.onResume();WatchConnection.retry(this);if(::content.isInitialized)refresh()}
 override fun onDestroy(){unregisterReceiver(receiver);super.onDestroy()}
 override fun onSaveInstanceState(out:Bundle){out.putString("room",roomId);out.putString("name",roomName);super.onSaveInstanceState(out)}
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun rounded(color:Int,radius:Int=20)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
 private fun text(value:String,size:Float=14f,color:Int=white,bold:Boolean=false):TextView=TextView(this).apply{text=value;textSize=size;setTextColor(color);gravity=Gravity.CENTER;setPadding(dp(4),dp(5),dp(4),dp(5));if(bold)setTypeface(typeface,Typeface.BOLD)}
 private fun add(view:View,margin:Int=5){content.addView(view,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(margin);bottomMargin=dp(margin)})}
 private fun button(label:String,accent:Boolean=false,action:()->Unit){add(Button(this).apply{text=label;isAllCaps=false;textSize=14f;setTextColor(if(accent)bg else white);background=rounded(if(accent)sage else panel,24);minHeight=dp(43);setPadding(dp(10),dp(7),dp(10),dp(7));setOnClickListener{action()}},5)}
 private fun heading(title:String,sub:String){add(text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),11f,muted),0);add(text(title,21f,white,true),4);if(sub.isNotBlank())add(text(sub,11f,muted),0)}
 private fun render(){
  if(compose)return;content.removeAllViews()
  val room=roomId
  if(room==null){
   val snapshot=WatchStore.snapshot(this);heading("Your people",if(busy)"Connecting to your phone…"else"Hasbreo Message")
   if(status.isNotBlank())add(text(status,11f,muted))
   if(snapshot==null){add(text("A little closer.\nRight on your wrist.",16f,white),12);add(text("1. Sign in on your phone\n2. Open Settings\n3. Connect your watch",12f,muted),8);button("Connect",true){refresh()}}
   else{
    val cached=Date(snapshot.optLong("cachedAt"));add(text("Saved ${DateFormat.getTimeInstance(DateFormat.SHORT).format(cached)}",10f,muted),0)
    val rooms=snapshot.optJSONArray("rooms")?:JSONArray();if(rooms.length()==0)add(text("Start a conversation on your phone.",13f,muted),12)
    for(i in 0 until minOf(rooms.length(),30)){
     val r=rooms.getJSONObject(i);val unread=r.optInt("unread");val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=rounded(panel);setPadding(dp(12),dp(10),dp(12),dp(10));isClickable=true;isFocusable=true;contentDescription=r.optString("name")+if(unread>0)" $unread unread"else"";setOnClickListener{roomId=r.getString("id");roomName=r.optString("name","Conversation");status="";render();refresh()}}
     card.addView(text(r.optString("name")+if(unread>0)" · $unread"else"",14f,if(unread>0)sage else white,true));card.addView(text(r.optString("preview").take(80),11f,muted));add(card,5)
    };button(if(busy)"Refreshing…"else"Refresh"){refresh()}
   }
   val outbox=WatchStore.queued(this);if(outbox.length()>0)button("${outbox.length()} waiting ${if(outbox.length()==1)"reply"else"replies"}"){showOutbox()}
   button("Quick replies & privacy"){settings()}
  }else{
   button("‹ Your people"){roomId=null;status="";render()};heading(roomName,if(busy)"Refreshing…"else"Through your phone")
   if(status.isNotBlank())add(text(status,11f,muted))
   val thread=WatchStore.messages(this,room);val messages=thread?.optJSONArray("messages")?:JSONArray()
   if(messages.length()==0)add(text("No saved messages yet. Refresh while your phone is connected.",12f,muted),12)
   for(i in maxOf(0,messages.length()-20) until messages.length()){
    val m=messages.getJSONObject(i);val mine=m.optBoolean("mine");val bubble=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(9),dp(10),dp(8));background=rounded(if(mine)Color.rgb(72,95,84)else panel,16)}
    if(!mine)bubble.addView(text(m.optString("sender"),10f,sage))
    bubble.addView(text(m.optString("body"),14f,if(m.optBoolean("deleted"))Color.rgb(215,145,153)else white));bubble.addView(text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.optLong("ts"))),10f,muted))
    bubble.setOnLongClickListener{reactions(m.optString("id"));true};add(bubble,4)
   }
   button("Reply",true){editor("")};button("Speak a reply"){speak()};button("Quick reply"){presets()}
   button("Soundboard"){soundboard()}
   button("Mark read"){request("read",room){status="Marked read";render()}}
   button("Open on phone"){request("open",room){status="Ready on your phone";render()}}
   button("Refresh"){refresh()}
   button("Mute this chat"){request("mute",room,JSONObject().put("muted",true)){status="Chat muted. Unmute in phone chat details.";render()}}
   scroll.post{scroll.smoothScrollTo(0,content.height)}
  }
 }
 private fun refresh(){if(busy||compose)return;busy=true;val room=roomId;render();WatchConnection.request(this,if(room==null)"snapshot"else"messages",room){result,error->busy=false;status=error?:"";if(!compose)render();if(result!=null&&room!=null)WatchConnection.request(this,"read",room)}}
 private fun request(action:String,room:String?,data:JSONObject=JSONObject(),done:(JSONObject)->Unit){WatchConnection.request(this,action,room,data){result,error->if(error!=null){status=error;render()}else if(result!=null)done(result)}}
 private fun editor(initial:String){
  compose=true;content.removeAllViews();heading("A little reply",roomName)
  val field=EditText(this).apply{setText(initial);hint="Your message";textSize=16f;setTextColor(white);setHintTextColor(muted);background=rounded(panel,15);setPadding(dp(12),dp(12),dp(12),dp(12));inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE;maxLines=5;minLines=2};add(field,10)
  button("Send",true){val value=field.text.toString().trim();if(value.isNotBlank()){compose=false;status="Sending…";render();WatchConnection.request(this,"send",roomId,JSONObject().put("text",value)){result,error->status=if(result?.optBoolean("sent")==true)"Sent ✓"else error?:"Reply is waiting for your phone.";if(result?.optBoolean("sent")==true){getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(35,VibrationEffect.DEFAULT_AMPLITUDE));refresh()};render()}}}
  button("Cancel"){compose=false;render()}
 }
 private fun speak(){try{voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT,"Reply to $roomName"))}catch(_:Exception){editor("");Toast.makeText(this,"Use your watch keyboard’s microphone",Toast.LENGTH_LONG).show()}}
 private fun soundboard(){val room=roomId?:return;request("sounds",room){result->val sounds=result.optJSONArray("sounds")?:JSONArray();if(sounds.length()==0){AlertDialog.Builder(this).setMessage("Upload sounds using the web or phone app first.").setPositiveButton("OK",null).show();return@request};AlertDialog.Builder(this).setTitle("Your sounds").setItems((0 until sounds.length()).map{sounds.getJSONObject(it).optString("name")}.toTypedArray()){_,i->val sound=sounds.getJSONObject(i);val actions=if(result.optBoolean("inCall"))arrayOf("Send in chat","Play in phone call")else arrayOf("Send in chat");AlertDialog.Builder(this).setTitle(sound.optString("name")).setItems(actions){_,choice->request(if(choice==0)"sound"else"soundCall",room,JSONObject().put("soundId",sound.getString("id"))){status=if(choice==0)"Sound sent ✓"else"Played in phone call";refresh()}}.setNegativeButton("Cancel",null).show()}.setNegativeButton("Close",null).show()}}
 private fun presets(){val saved=JSONArray(Vault(this).get("quickReplies")?:WatchStore.snapshot(this)?.optJSONArray("quickReplies")?.toString()?:"[\"On my way\",\"Give me a minute\",\"Can’t talk right now\",\"Sounds good!\",\"👍\",\"❤️\"]");AlertDialog.Builder(this).setTitle("Quick reply").setItems((0 until saved.length()).map{saved.getString(it)}.toTypedArray()){_,i->editor(saved.getString(i))}.setNegativeButton("Cancel",null).show()}
 private fun reactions(id:String){val options=arrayOf("👍","❤️","😂","🙌","👀");AlertDialog.Builder(this).setTitle("A little reaction").setItems(options){_,i->request("reaction",roomId,JSONObject().put("eventId",id).put("emoji",options[i])){status="Reaction sent";render()}}.setNegativeButton("Cancel",null).show()}
 private fun showOutbox(){val list=WatchStore.queued(this);val labels=(0 until list.length()).map{list.getJSONObject(it).optString("text").take(70)}.toTypedArray();AlertDialog.Builder(this).setTitle("Waiting for your phone").setItems(labels){_,i->val r=list.getJSONObject(i);AlertDialog.Builder(this).setMessage(r.optString("text")).setPositiveButton("Retry"){_,_->WatchConnection.retry(this);status="Retrying queued replies…";render()}.setNegativeButton("Discard"){_,_->WatchStore.removeQueued(this,r.getString("requestId"));render()}.show()}.setPositiveButton("Retry all"){_,_->WatchConnection.retry(this)}.setNegativeButton("Close",null).show()}
 private fun settings(){AlertDialog.Builder(this).setTitle("Your wrist, your way").setItems(arrayOf("Edit quick replies","Clear saved chats","Forget phone & clear outbox")){_,which->when(which){
  0->{val field=EditText(this).apply{setText((0 until JSONArray(Vault(this@MainActivity).get("quickReplies")?:"[]").length()).joinToString("\n"){JSONArray(Vault(this@MainActivity).get("quickReplies")?:"[]").getString(it)});hint="One reply per line";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=4};AlertDialog.Builder(this).setTitle("Quick replies").setView(field).setPositiveButton("Save"){_,_->val replies=field.text.toString().lines().map{it.trim()}.filter{it.isNotBlank()}.take(12);Vault(this).put("quickReplies",JSONArray(replies).toString())}.setNegativeButton("Cancel",null).show()}
  1->{val v=Vault(this);val rooms=JSONArray(v.get("cachedRooms")?:"[]");for(i in 0 until rooms.length())v.put("room:"+rooms.optString(i),null);v.put("cachedRooms",null);roomId=null;status="Saved chats cleared.";render()}
  2->{AlertDialog.Builder(this).setMessage("Remove saved chats and waiting replies from this watch?").setPositiveButton("Forget"){_,_->WatchConnection.request(this,"disconnect");WatchStore.clear(this);roomId=null;status="Saved chats cleared. Phone access is revoked once connected.";render()}.setNegativeButton("Cancel",null).show()}
 }}.setNegativeButton("Close",null).show()}
}
