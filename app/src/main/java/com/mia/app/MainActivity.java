package com.mia.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private final String PREF="mia_native_v1";
    private final String[] MODES={"Normal","Bro","Serio","Humor","Diabólico","Salseo"};
    private final String[] MODE_DESC={"Natural, cercana y con carácter propio.","Como mejor amigo, coloquial y directo.","Directa y concisa, sin relleno.","Con humor cuando encaje, sin forzarlo.","Sarcástica y ácida.","Cotilleo y drama."};
    private final String[] AVATARS={"🐱","🐺","🦊","🐰","🐻","🐼","🐯","🐲","😈","👼","🤖","👾"};
    private final String[] AVATAR_NAMES={"Gato","Lobo","Zorra","Conejo","Oso","Panda","Tigre","Dragón","Demonio","Ángel","Robot","Alien"};
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> history=new ArrayList<>();
    private LinearLayout root, messages, quickBar;
    private ScrollView scroll;
    private EditText input;
    private TextView status, avatarView;
    private ProgressBar progress;
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private String apiKey="", mode="Normal", avatar="🐱", userName="", team="";
    private int intensity=5, messageCount=0;
    private boolean autoVoice=false, busy=false;
    private String pendingImage="", pendingMime="image/jpeg";
    private static final int PICK_IMAGE=101, SPEECH=102;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences(PREF,MODE_PRIVATE);
        apiKey=prefs.getString("api_key","");
        mode=prefs.getString("mode","Normal");
        avatar=prefs.getString("avatar","🐱");
        userName=prefs.getString("name","");
        team=prefs.getString("team","");
        intensity=prefs.getInt("intensity",5);
        autoVoice=prefs.getBoolean("auto_voice",false);
        messageCount=prefs.getInt("message_count",0);
        loadHistory();
        getWindow().setStatusBarColor(Color.rgb(10,11,16));
        getWindow().setNavigationBarColor(Color.rgb(10,11,16));
        tts=new TextToSpeech(this, result -> { if(tts!=null) tts.setLanguage(new Locale("es","ES")); });
        buildHome();
        if(apiKey.isEmpty()) new Handler().postDelayed(this::showSettings,500);
    }

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(null,1);return t;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
    private TextView button(String s,Runnable r){
        TextView t=text(s,14,Color.WHITE,true);t.setGravity(Gravity.CENTER);pad(t,14,11,14,11);
        t.setBackground(bg(Color.rgb(32,34,43),Color.rgb(65,67,78),14));
        t.setOnClickListener(v->r.run());return t;
    }

    private void buildHome(){
        root=column();root.setBackgroundColor(Color.rgb(10,11,16));setContentView(root);
        LinearLayout top=row();pad(top,16,10,16,10);
        TextView mark=text("M",27,Color.rgb(219,174,83),true);
        TextView ia=text("IA",23,Color.rgb(45,143,255),true);
        LinearLayout brand=row();brand.addView(mark);brand.addView(ia);
        LinearLayout.LayoutParams bp=lp(0,-2);bp.weight=1;top.addView(brand,bp);
        TextView profile=button("◉",this::showProfile);top.addView(profile,lp(44,42));
        TextView settings=button("⚙",this::showSettings);LinearLayout.LayoutParams sp=lp(44,42);sp.leftMargin=dp(8);top.addView(settings,sp);
        root.addView(top);
        View line=new View(this);line.setBackgroundColor(Color.rgb(43,45,55));root.addView(line,lp(-1,1));
        LinearLayout avRow=row();pad(avRow,16,12,16,6);
        avatarView=text(avatar,34,Color.WHITE,false);avatarView.setGravity(Gravity.CENTER);avatarView.setBackground(bg(Color.rgb(25,27,35),Color.rgb(205,164,78),40));avRow.addView(avatarView,lp(56,56));
        LinearLayout greeting=column();TextView title=text("MIA",18,Color.WHITE,true);TextView sub=text("Tu inteligencia artificial personal",12,0xff9b9eaa,false);greeting.addView(title);greeting.addView(sub);
        LinearLayout.LayoutParams gp=lp(0,-2);gp.weight=1;gp.leftMargin=dp(12);avRow.addView(greeting,gp);
        TextView change=button("Avatar",this::showAvatars);avRow.addView(change);
        root.addView(avRow);
        status=text(apiKey.isEmpty()?"Configura tu clave de Gemini en Ajustes para empezar":"En línea · Gemini 2.0 Flash",11,0xffa6a8b2,false);
        pad(status,18,2,18,8);root.addView(status);
        scroll=new ScrollView(this);scroll.setFillViewport(true);messages=column();pad(messages,14,8,14,14);scroll.addView(messages);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,0,1);root.addView(scroll,slp);
        if(history.isEmpty()) addWelcome(); else for(JSONObject m:history) renderMessage(m.optString("role"),m.optString("text"),false);
        quickBar=row();pad(quickBar,12,6,12,8);
        String[] chips={"Sorpréndeme","Ayúdame con algo","Explícame algo"};
        for(String c:chips){TextView chip=text(c,11,0xffd8b66e,false);pad(chip,9,8,9,8);chip.setBackground(bg(Color.rgb(24,25,32),Color.rgb(73,64,45),20));LinearLayout.LayoutParams cp=lp(-2,-2);cp.rightMargin=dp(6);quickBar.addView(chip,cp);chip.setOnClickListener(v->{input.setText(c.equals("Sorpréndeme")?"Sorpréndeme":c.equals("Ayúdame con algo")?"¿En qué puedes ayudarme?":"Explícame algo interesante");sendMessage();});}
        root.addView(quickBar);
        LinearLayout composer=row();pad(composer,10,8,10,10);composer.setBackgroundColor(Color.rgb(15,16,22));
        TextView attach=button("+",this::pickImage);composer.addView(attach,lp(42,46));
        input=new EditText(this);input.setTextColor(Color.WHITE);input.setHintTextColor(0xff777b88);input.setHint("Escribe a MIA...");input.setTextSize(15);input.setMaxLines(5);input.setBackground(bg(Color.rgb(29,31,39),0,18));pad(input,13,8,13,8);
        LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(0,dp(46),1);ilp.leftMargin=dp(7);ilp.rightMargin=dp(7);composer.addView(input,ilp);
        TextView mic=button("🎙",this::startSpeech);composer.addView(mic,lp(42,46));
        TextView send=button("➤",this::sendMessage);LinearLayout.LayoutParams sendp=lp(44,46);sendp.leftMargin=dp(6);composer.addView(send,sendp);
        root.addView(composer);
    }

    private void addWelcome(){
        LinearLayout box=column();pad(box,12,34,12,28);box.setGravity(Gravity.CENTER);
        TextView logo=text("MIA",34,Color.rgb(219,174,83),true);box.addView(logo);
        TextView hello=text("¿En qué te ayudo hoy?",19,Color.WHITE,true);LinearLayout.LayoutParams hp=lp(-2,-2);hp.topMargin=dp(8);box.addView(hello,hp);
        TextView hint=text("Pregunta, crea, analiza o simplemente charla conmigo.",13,0xff999ca8,false);LinearLayout.LayoutParams h2=lp(-2,-2);h2.topMargin=dp(5);box.addView(hint,h2);
        messages.addView(box);
    }
    private void renderMessage(String role,String value,boolean animate){
        if(messages==null)return;
        LinearLayout wrap=column();wrap.setGravity(role.equals("user")?Gravity.RIGHT:Gravity.LEFT);
        TextView bubble=text(value,15,role.equals("user")?Color.WHITE:0xffe7e7eb,false);
        pad(bubble,14,11,14,11);bubble.setBackground(bg(role.equals("user")?Color.rgb(35,77,125):Color.rgb(28,30,38),role.equals("user")?0xff386eaa:0xff383b47,17));
        LinearLayout.LayoutParams p=lp(-2,-2);p.bottomMargin=dp(10);p.leftMargin=dp(role.equals("user")?48:0);p.rightMargin=dp(role.equals("user")?0:35);
        wrap.addView(bubble,p);messages.addView(wrap);
        if(animate)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void addMessage(String role,String value){
        try{JSONObject o=new JSONObject();o.put("role",role);o.put("text",value);history.add(o);while(history.size()>40)history.remove(0);saveHistory();}catch(Exception ignored){}
        renderMessage(role,value,true);
    }
    private void saveHistory(){JSONArray a=new JSONArray();for(JSONObject o:history)a.put(o);prefs.edit().putString("history",a.toString()).apply();}
    private void loadHistory(){try{JSONArray a=new JSONArray(prefs.getString("history","[]"));for(int i=0;i<a.length();i++)history.add(a.getJSONObject(i));}catch(Exception ignored){}}
    private void sendMessage(){
        if(busy)return;
        String q=input.getText().toString().trim();
        if(q.isEmpty()&&pendingImage.isEmpty())return;
        if(apiKey.isEmpty()){showSettings();return;}
        if(messages.getChildCount()==1 && history.isEmpty())messages.removeAllViews();
        input.setText("");addMessage("user",q.isEmpty()?"Analiza esta imagen.":q);
        messageCount++;prefs.edit().putInt("message_count",messageCount).apply();
        final String image=pendingImage,mime=pendingMime;pendingImage="";
        busy=true;status.setText("MIA está pensando…");progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);root.addView(progress,root.indexOfChild(quickBar));
        io.execute(()->{
            try{
                String answer=askGemini(q,image,mime);
                runOnUiThread(()->{finishReply(answer,null);});
            }catch(Exception e){runOnUiThread(()->finishReply(null,e.getMessage()));}
        });
    }
    private void finishReply(String answer,String error){
        busy=false;if(progress!=null){root.removeView(progress);progress=null;}
        status.setText(error==null?"En línea · Gemini 2.0 Flash":"No se pudo conectar · revisa Ajustes");
        String value=error==null?answer:"⚠️ "+error;
        if(value==null||value.trim().isEmpty())value="No he recibido una respuesta. Inténtalo otra vez.";
        addMessage("assistant",value);if(error==null&&autoVoice&&tts!=null)tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"mia-reply");
    }

    private String askGemini(String question,String image,String mime)throws Exception{
        JSONArray contents=new JSONArray();
        for(JSONObject old:history){
            String role=old.optString("role");
            if(role.equals("system"))continue;
            JSONObject c=new JSONObject();c.put("role",role.equals("assistant")?"model":"user");
            JSONArray parts=new JSONArray();parts.put(new JSONObject().put("text",old.optString("text")));
            c.put("parts",parts);contents.put(c);
        }
        if(!image.isEmpty()){
            JSONObject c=new JSONObject();c.put("role","user");
            JSONArray parts=new JSONArray();parts.put(new JSONObject().put("text",question.isEmpty()?"Analiza esta imagen.":question));
            parts.put(new JSONObject().put("inlineData",new JSONObject().put("mimeType",mime).put("data",image)));
            contents.put(c.put("parts",parts));
        }
        JSONObject body=new JSONObject();
        body.put("system_instruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",buildSystem()))));
        body.put("contents",contents);
        body.put("generationConfig",new JSONObject().put("maxOutputTokens",1400).put("temperature",0.85));
        URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key="+URLEncoder.encode(apiKey,"UTF-8"));
        HttpURLConnection con=(HttpURLConnection)url.openConnection();con.setRequestMethod("POST");con.setConnectTimeout(25000);con.setReadTimeout(60000);con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json");
        try(OutputStream os=con.getOutputStream()){os.write(body.toString().getBytes("UTF-8"));}
        int code=con.getResponseCode();InputStream is=code>=200&&code<300?con.getInputStream():con.getErrorStream();
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=is.read(buf))!=-1)out.write(buf,0,n);
        String response=out.toString("UTF-8");con.disconnect();
        JSONObject json=new JSONObject(response);
        if(code<200||code>=300){String msg=json.optJSONObject("error")!=null?json.getJSONObject("error").optString("message","Error HTTP "+code):"Error HTTP "+code;throw new Exception(msg);}
        JSONArray candidates=json.optJSONArray("candidates");
        if(candidates==null||candidates.length()==0)throw new Exception("Gemini no ha devuelto respuesta.");
        JSONArray parts=candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
        StringBuilder result=new StringBuilder();for(int i=0;i<parts.length();i++)result.append(parts.getJSONObject(i).optString("text",""));
        return result.toString().trim();
    }
    private String buildSystem(){
        String description="Normal, cercana y con carácter propio.";
        for(int i=0;i<MODES.length;i++)if(MODES[i].equals(mode))description=MODE_DESC[i];
        StringBuilder s=new StringBuilder("Eres MIA, una IA personal avanzada con carácter propio y conocimiento experto. ");
        if(!userName.isEmpty())s.append("El usuario se llama ").append(userName).append(". ");
        if(!team.isEmpty())s.append("Su equipo favorito es ").append(team).append(". ");
        s.append("Modo: ").append(description).append(" Intensidad ").append(intensity).append("/10. ");
        s.append("CAPACIDADES: programación, matemáticas, ciencias, escritura, diseño, idiomas, historia, filosofía, análisis de imágenes y documentos. ");
        s.append("REGLAS: responde en el idioma del usuario; sé directa, útil y natural; no empieces con 'Claro' ni 'Por supuesto'. Máximo 3 párrafos.");
        return s.toString();
    }

    private void pickImage(){Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(Intent.createChooser(i,"Selecciona una imagen"),PICK_IMAGE);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_IMAGE&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try{Uri u=data.getData();pendingMime=getContentResolver().getType(u);if(pendingMime==null)pendingMime="image/jpeg";
                InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);in.close();
                pendingImage=android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);
                status.setText("Imagen adjunta · escribe qué quieres que analice");
            }catch(Exception e){toast("No se pudo leer la imagen");}
        }
    }
    private void startSpeech(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){toast("El reconocimiento de voz no está disponible");return;}
        try{
            recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){status.setText("Escuchando…");}
                public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){}
                public void onEndOfSpeech(){status.setText("Procesando voz…");}
                public void onError(int e){status.setText("En línea");toast("No se ha reconocido la voz");recognizer.destroy();}
                public void onResults(Bundle b){ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()){input.setText(a.get(0));input.setSelection(input.length());}status.setText("En línea");recognizer.destroy();}
                public void onPartialResults(Bundle b){} public void onEvent(int t,Bundle b){}
            });
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-ES");recognizer.startListening(i);
        }catch(Exception e){toast("No se pudo iniciar el micrófono");}
    }
    private void showSettings(){
        LinearLayout form=column();pad(form,20,12,20,6);
        EditText key=new EditText(this);key.setSingleLine(true);key.setTextColor(Color.WHITE);key.setHintTextColor(0xff999999);key.setHint("AIza… · clave de Google AI Studio");key.setText(apiKey);key.setInputType(129);form.addView(text("Clave API de Gemini",13,0xffd9b46c,true));form.addView(key,lp(-1,50));
        EditText name=new EditText(this);name.setSingleLine(true);name.setTextColor(Color.WHITE);name.setHint("Tu nombre (opcional)");name.setText(userName);form.addView(text("Perfil",13,0xffd9b46c,true));form.addView(name,lp(-1,48));
        EditText teamIn=new EditText(this);teamIn.setSingleLine(true);teamIn.setTextColor(Color.WHITE);teamIn.setHint("Equipo favorito (opcional)");teamIn.setText(team);form.addView(teamIn,lp(-1,48));
        Spinner modes=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,MODES);modes.setAdapter(adapter);modes.setSelection(Arrays.asList(MODES).indexOf(mode));form.addView(text("Personalidad de MIA",13,0xffd9b46c,true));form.addView(modes);
        SeekBar intensityBar=new SeekBar(this);intensityBar.setMax(10);intensityBar.setProgress(intensity);form.addView(text("Intensidad: "+intensity,13,0xffd9b46c,true));form.addView(intensityBar);
        CheckBox voice=new CheckBox(this);voice.setText("Leer respuestas en voz alta");voice.setTextColor(Color.WHITE);voice.setButtonTintList(android.content.res.ColorStateList.valueOf(0xffd9b46c));voice.setChecked(autoVoice);form.addView(voice);
        ScrollView sc=new ScrollView(this);sc.addView(form);
        new AlertDialog.Builder(this).setTitle("Ajustes de MIA").setView(sc).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{
            apiKey=key.getText().toString().trim();userName=name.getText().toString().trim();team=teamIn.getText().toString().trim();mode=modes.getSelectedItem().toString();intensity=intensityBar.getProgress();autoVoice=voice.isChecked();
            prefs.edit().putString("api_key",apiKey).putString("name",userName).putString("team",team).putString("mode",mode).putInt("intensity",intensity).putBoolean("auto_voice",autoVoice).apply();
            status.setText(apiKey.isEmpty()?"Falta configurar la clave de Gemini":"En línea · Gemini 2.0 Flash");toast("Ajustes guardados");
        }).show();
    }
    private void showAvatars(){
        String[] names=new String[AVATAR_NAMES.length];for(int i=0;i<names.length;i++)names[i]=AVATARS[i]+"  "+AVATAR_NAMES[i];
        new AlertDialog.Builder(this).setTitle("Elige tu avatar").setItems(names,(d,which)->{avatar=AVATARS[which];prefs.edit().putString("avatar",avatar).apply();avatarView.setText(avatar);}).show();
    }
    private void showProfile(){
        String info="Mensajes enviados: "+messageCount+"\nPersonalidad: "+mode+" · "+intensity+"/10\nAvatar: "+avatar+"\n\nMIA conserva tus conversaciones en este dispositivo.";
        new AlertDialog.Builder(this).setTitle("Perfil de MIA").setMessage(info).setPositiveButton("Ajustes",(d,w)->showSettings()).setNeutralButton("Nuevo chat",(d,w)->newChat()).setNegativeButton("Cerrar",null).show();
    }
    private void newChat(){
        history.clear();saveHistory();messages.removeAllViews();addWelcome();toast("Chat nuevo");
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override public void onBackPressed(){new AlertDialog.Builder(this).setMessage("¿Quieres salir de MIA?").setPositiveButton("Salir",(d,w)->finish()).setNegativeButton("Seguir aquí",null).show();}
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}if(recognizer!=null)recognizer.destroy();io.shutdown();super.onDestroy();}
}
