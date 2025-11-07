package dev.xoventech.tunnel.vpn.adapter;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Objects;
import dev.xoventech.tunnel.vpn.harliesApplication;
import dev.xoventech.tunnel.vpn.R;
import dev.xoventech.tunnel.vpn.config.ConfigUtil;
import dev.xoventech.tunnel.vpn.config.SettingsConstants;
import dev.xoventech.tunnel.vpn.utils.Model;
import dev.xoventech.tunnel.vpn.utils.util;
import dev.xoventech.tunnel.vpn.view.swipe.DragListView;
import dev.xoventech.tunnel.vpn.view.swipe.ItemAdapter;
import io.michaelrocks.paranoid.Obfuscate;

@Obfuscate
public class ConfigSpinnerAdapter extends AppCompatActivity implements SettingsConstants {

	private DragListView ConfigListView;
	private SearchView searchview;
	private ItemAdapter listAdapter;
    private String ConfigType;
	private SharedPreferences mPref;
	private SharedPreferences.Editor mEditor;
	private ConfigUtil mConfig;
	private View show_random_ly;
	private final ArrayList<Model> arrayList = new ArrayList<>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_adapters);
		mConfig = ConfigUtil.getInstance(ConfigSpinnerAdapter.this);
		mPref = harliesApplication.getPrivateSharedPreferences();
		mEditor = mPref.edit();
		getWindow().setStatusBarColor(mConfig.getColorAccent());
		Bundle bundle = getIntent().getExtras();
		if (bundle == null){
			finish();
			return;
		}
		ConfigType = bundle.getString("mConfigType");
		Toolbar mToolbar = findViewById(R.id.toolbar);
		if (Objects.equals(ConfigType, "0")){
			mToolbar.setTitle("Servers");
		}else if (Objects.equals(ConfigType, "1")){
			mToolbar.setTitle("Networks");
		}
		mToolbar.setBackgroundColor(mConfig.getColorAccent());
		mToolbar.setTitleTextColor(Color.WHITE);
		setSupportActionBar(mToolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		mToolbar.setNavigationOnClickListener(v -> ConfigSpinnerAdapter.this.finish());
		ConfigListView = findViewById(R.id.config_listview);
        show_random_ly = findViewById(R.id.show_random_l);
		LinearLayoutManager mLinearLayoutManager = new LinearLayoutManager(ConfigSpinnerAdapter.this);
		if (Objects.equals(ConfigType, "0")){
			show_random_ly.setVisibility(View.VISIBLE);
		}else if (Objects.equals(ConfigType, "1")){
			show_random_ly.setVisibility(View.GONE);
		}
		ConfigListView.setBackgroundColor(Color.TRANSPARENT);
		ConfigListView.getRecyclerView().setVerticalScrollBarEnabled(false);
		ConfigListView.setLayoutManager(mLinearLayoutManager);
		listAdapter = new ItemAdapter(ConfigSpinnerAdapter.this,getConfigAdapter(ConfigType), R.layout.server_list, R.id.image, Integer.parseInt(ConfigType));
		ConfigListView.setAdapter(listAdapter, true);
		ConfigListView.setCanDragHorizontally(false);
		ConfigListView.setCanDragVertically(true);
		ConfigListView.setDragListListener(new DragListView.DragListListenerAdapter() {
            @Override
			public void onItemDragEnded(int fromPosition, int toPosition) {
				loadNewJS(listAdapter.getNewJS(),toPosition);
			}
		});
		listAdapter.setOnSelectedSerListener(new ItemAdapter.OnSelectedSerListener() {
			@Override
			public void onSelectSer(String charText) {
				getConfigAdapter(ConfigType);
				int p = 0;
				for (Model model : arrayList) {
					String name = model.getName();
					int postition = model.getPostition();
					if (name.equals(charText)){
						p = postition;
					}
				}
				if (Objects.equals(ConfigType, "0")){
					mEditor.putInt(SERVER_POSITION,p).apply();
					mEditor.putBoolean("isRandom", false).apply();
				}else if (Objects.equals(ConfigType, "1")){
					mEditor.putInt(NETWORK_POSITION,p).apply();
				}
				finish();
			}
			@Override
			public void onReloadConfig(int position) {
				loadNewJS(listAdapter.getNewJS(),position);
				mLinearLayoutManager.scrollToPosition(position);
			}
		});
		AppCompatImageView mSelectedItem = findViewById(R.id.mSelectedItem);
		if (mPref.getBoolean("isRandom",false)){
			mSelectedItem.setImageResource(R.drawable.ic_item_selected);
			mSelectedItem.setColorFilter(getResources().getColor(R.color.connect_color), PorterDuff.Mode.SRC_IN);
		}else{
			mSelectedItem.setImageResource(R.drawable.ic_item_unselected);
			mSelectedItem.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
		}
		show_random_ly.setOnClickListener(p1 -> {
			mEditor.putBoolean("isRandom", true).apply();
			mSelectedItem.setImageResource(R.drawable.ic_item_selected);
			mSelectedItem.setColorFilter(getResources().getColor(R.color.connect_color), PorterDuff.Mode.SRC_IN);
            finish();
        });
		if (reloadAdapterView())mLinearLayoutManager.scrollToPosition(Objects.equals(ConfigType, "0")?mPref.getInt(SERVER_POSITION,0):mPref.getInt(NETWORK_POSITION,0));
		if (mPref.getBoolean("show_random_layout",false)&&Objects.equals(ConfigType, "0")){
			show_random_ly.setVisibility(listAdapter.getItemList().size()>=2?View.VISIBLE:View.GONE);
		}else{
			show_random_ly.setVisibility(View.GONE);
		}
	}


	private boolean reloadAdapterView(){
		if (searchview!=null){
			String research = searchview.getQuery().toString();
			if (TextUtils.isEmpty(research)){
				return true;
			}else {
				listAdapter.filter(research);
				setupListRecyclerView(ConfigListView,listAdapter);
				return false;
			}
		}
		return false;
	}


	private void loadNewJS(String data,int position){
        try {
			if (Objects.equals(ConfigType, "0")){
				String a = SERVER_TYPE_OVPN;
				mEditor.putInt(SERVER_POSITION,position).apply();
				if (mPref.getInt(manual_tunnel_radio_key, 0)==0){
					a = SERVER_TYPE_OVPN;
				}else if (mPref.getInt(manual_tunnel_radio_key, 0)==1){
					a = SERVER_TYPE_UDP_HYSTERIA_V1;
				}else if (mPref.getInt(manual_tunnel_radio_key, 0)==2){
					a = SERVER_TYPE_SSH;
				}else if (mPref.getInt(manual_tunnel_radio_key, 0)==3){
					a = SERVER_TYPE_DNS;
				}else if (mPref.getInt(manual_tunnel_radio_key, 0)==4){
					a = SERVER_TYPE_V2RAY;
				}else if (mPref.getInt(manual_tunnel_radio_key, 0)==5){
					a = SERVER_TYPE_OPEN_CONNECT;
				}
				mEditor.putString(a, data).apply();
			}else if (Objects.equals(ConfigType, "1")){
				String b = SERVER_TYPE_OVPN;
				mEditor.putInt(NETWORK_POSITION,position).apply();
				if (mPref.getInt(manual_tunnel_radio_key, 0)==0 || mPref.getInt(manual_tunnel_radio_key, 0)==2 || mPref.getInt(manual_tunnel_radio_key, 0)==5){
					b = LOAD_OVPN_SSH_OCS_TWEAKS_KEY;
				}else if (mPref.getInt(manual_tunnel_radio_key, 0)==1){
					b = LOAD_UDP_TWEAKS_KEY;
				}else if (mPref.getInt(manual_tunnel_radio_key, 0)==3){
					b = LOAD_DNS_TWEAKS_KEY;
				}else if (mPref.getInt(manual_tunnel_radio_key, 0)==4){
					b = LOAD_V2RAY_TWEAKS_KEY;
				}
				mEditor.putString(b, data).apply();
			}
        } catch (Exception ignored) {
        }
	}

	private void setupListRecyclerView(DragListView mDragListView,ItemAdapter listAdapter) {
		LinearLayoutManager mLinearLayoutManager = new LinearLayoutManager(ConfigSpinnerAdapter.this);
		mDragListView.setLayoutManager(mLinearLayoutManager);
		mDragListView.setAdapter(listAdapter, true);
		mDragListView.setCanDragHorizontally(false);
		mDragListView.setCanDragVertically(true);
		mLinearLayoutManager.scrollToPosition(Objects.equals(ConfigType, "0")?mPref.getInt(SERVER_POSITION,0):mPref.getInt(NETWORK_POSITION,0));
	}

	private ArrayList<Pair<Long, String>> getConfigAdapter(String t) {
		ArrayList<Pair<Long, String>> mItemArray = new ArrayList<>();
		arrayList.clear();
		JSONArray jar = null;
		try {
			if (Objects.equals(t, "0")){
				jar = getServerArrayDragaPosition();
			}else if (Objects.equals(t, "1")){
				jar = getNetworkArrayDragaPosition();
			}
			for (int i=0;i < jar.length();i++) {
				String name = jar.getJSONObject(i).getString("Name");
				Model model = new Model(name, i);
				arrayList.add(model);
				mItemArray.add(new Pair<>((long) i, jar.getJSONObject(i).toString()));
			}
			return mItemArray;
		} catch (Exception e) {
			return null;
		}
	}

	public JSONArray getServerArrayDragaPosition(){
		try {
			JSONArray jar = new JSONArray();
			if (mPref.getInt(manual_tunnel_radio_key, 0)==0){
				JSONArray jarr1 = new JSONArray(mPref.getString(SERVER_TYPE_OVPN,"[]"));
				for (int i=0;i < jarr1.length();i++){
					if (jarr1.getJSONObject(i).getInt("serverType")==0){
						jar.put(jarr1.getJSONObject(i));
					}
				}
			}else if (mPref.getInt(manual_tunnel_radio_key, 0)==2){
				JSONArray jarr2 = new JSONArray(mPref.getString(SERVER_TYPE_SSH,"[]"));
				for (int i=0;i < jarr2.length();i++){
					if (jarr2.getJSONObject(i).getInt("serverType")==1){
						jar.put(jarr2.getJSONObject(i));
					}
				}
			}else if (mPref.getInt(manual_tunnel_radio_key, 0)==3){
				JSONArray jarr3 = new JSONArray(mPref.getString(SERVER_TYPE_DNS,"[]"));
				for (int i=0;i < jarr3.length();i++){
					if (jarr3.getJSONObject(i).getInt("serverType")==2){
						jar.put(jarr3.getJSONObject(i));
					}
				}
			}else if (mPref.getInt(manual_tunnel_radio_key, 0)==1){
				JSONArray jarr4 = new JSONArray(mPref.getString(SERVER_TYPE_UDP_HYSTERIA_V1,"[]"));
				for (int i=0;i < jarr4.length();i++){
					if (jarr4.getJSONObject(i).getInt("serverType")==4){
						jar.put(jarr4.getJSONObject(i));
					}
				}
			}else if (mPref.getInt(manual_tunnel_radio_key, 0)==4){
				JSONArray jarr5 = new JSONArray(mPref.getString(SERVER_TYPE_V2RAY,"[]"));
				for (int i=0;i < jarr5.length();i++){
					if (jarr5.getJSONObject(i).getInt("serverType")==3){
						jar.put(jarr5.getJSONObject(i));
					}
				}
			}else if (mPref.getInt(manual_tunnel_radio_key, 0)==5){
				JSONArray jarr6 = new JSONArray(mPref.getString(SERVER_TYPE_OPEN_CONNECT,"[]"));
				for (int i=0;i < jarr6.length();i++){
					if (jarr6.getJSONObject(i).getInt("serverType")==5){
						jar.put(jarr6.getJSONObject(i));
					}
				}
			}
			if (jar.length()>=2){
				mEditor.putBoolean("show_random_layout",true).apply();
			}else{
				mEditor.putBoolean("show_random_layout",false).apply();
			}
			return jar;
		} catch (JSONException e) {
			mEditor.putBoolean("isRandom", false).apply();
			mEditor.putBoolean("show_random_layout",false).apply();
			util.showToast("Error!", e.getMessage());}
		return null;
	}

	public JSONArray getNetworkArrayDragaPosition(){
		try {
			JSONArray jar = new JSONArray();
			if (mPref.getInt(manual_tunnel_radio_key, 0)==0 || mPref.getInt(manual_tunnel_radio_key, 0)==2 || mPref.getInt(manual_tunnel_radio_key, 0)==5){
				JSONArray jarr = new JSONArray(mPref.getString(LOAD_OVPN_SSH_OCS_TWEAKS_KEY,"[]"));
				for (int i=0;i < jarr.length();i++){
					JSONObject js = jarr.getJSONObject(i);
					if (js.getInt("proto_spin") == 0) {
						jar.put(jarr.getJSONObject(i));
					} else if (js.getInt("proto_spin") == 3) {
						jar.put(jarr.getJSONObject(i));
					} else if (js.getInt("proto_spin") == 4) {
						jar.put(jarr.getJSONObject(i));
					} else if (js.getInt("proto_spin") == 5) {
						jar.put(jarr.getJSONObject(i));
					}
				}
			}else if (mPref.getInt(manual_tunnel_radio_key, 0)==1){
				JSONArray jarr = new JSONArray(mPref.getString(LOAD_UDP_TWEAKS_KEY,"[]"));
				for (int i=0;i < jarr.length();i++) {
					JSONObject js = jarr.getJSONObject(i);
					if (js.getInt("proto_spin") == 1) {
						jar.put(jarr.getJSONObject(i));
					}
				}
			}else if (mPref.getInt(manual_tunnel_radio_key, 0)==3){
				JSONArray jarr = new JSONArray(mPref.getString(LOAD_DNS_TWEAKS_KEY,"[]"));
				for (int i=0;i < jarr.length();i++) {
					JSONObject js = jarr.getJSONObject(i);
					if (js.getInt("proto_spin") == 2) {
						jar.put(jarr.getJSONObject(i));
					}
				}
			}else if (mPref.getInt(manual_tunnel_radio_key, 0)==4){
				JSONArray jarr = new JSONArray(mPref.getString(LOAD_V2RAY_TWEAKS_KEY,"[]"));
				for (int i=0;i < jarr.length();i++) {
					JSONObject js = jarr.getJSONObject(i);
					if (js.getInt("proto_spin") == 6) {
						jar.put(jarr.getJSONObject(i));
					}
				}
			}
			return jar;
		} catch (JSONException e) {
			util.showToast("Error!", e.getMessage());}
		return null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		if (mConfig.getServerType().equals(SERVER_TYPE_V2RAY)) {
			getMenuInflater().inflate(R.menu.server_menu,menu);
		} else {
			if (mConfig.getServerType().equals(SERVER_TYPE_OVPN) || mConfig.getServerType().equals(SERVER_TYPE_SSH)){
				if (Objects.equals(ConfigType, "0")){
					getMenuInflater().inflate(R.menu.server_menu,menu);
				}else{
					getMenuInflater().inflate(R.menu.tweak_menu,menu);
				}
			}else {
				getMenuInflater().inflate(R.menu.server_menu,menu);
			}
		}
		searchview = (SearchView) menu.findItem(R.id.search).getActionView();
        assert searchview != null;
        searchview.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				return false;
			}
			@Override
			public boolean onQueryTextChange(String newText) {
				String text = newText;
				if (TextUtils.isEmpty(text)){
					listAdapter.filter("");
				}else {
					listAdapter.filter(text);
				}
				setupListRecyclerView(ConfigListView,listAdapter);
				show_random_ly.setVisibility(mPref.getBoolean("show_random_layout",false)&&Objects.equals(ConfigType, "0")&&listAdapter.getItemList().size()>=2?View.VISIBLE:View.GONE);
				return true;
			}
		});
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		if (item.getItemId()==R.id.add_custom){
			if (listAdapter!=null){
				if (listAdapter.getServerDialog()){
					listAdapter.addServer();
				}else{
					listAdapter.addPayload();
				}
			}
		}else if (item.getItemId()==R.id.add_payload){
			if (listAdapter!=null){
				listAdapter.getServerDialog();
				listAdapter.addPayload();
			}
		}else if (item.getItemId()==R.id.add_ssl){
			if (listAdapter!=null){
				listAdapter.getServerDialog();
				listAdapter.addSSL();
			}
		}else if (item.getItemId()==R.id.delete_custom){
			if (listAdapter!=null){
				listAdapter.getServerDialog();
				listAdapter.mDelete(Objects.equals(ConfigType, "0")?mPref.getInt(SERVER_POSITION,0):mPref.getInt(NETWORK_POSITION,0));
			}
		}
		return true;
	}
}
