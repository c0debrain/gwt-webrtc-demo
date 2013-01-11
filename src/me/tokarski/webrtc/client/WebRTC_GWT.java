package me.tokarski.webrtc.client;

import me.tokarski.webrtc.client.Call.CallStateListener;
import me.tokarski.webrtc.client.Call.Direction;
import me.tokarski.webrtc.client.PeopleWindow.PeopleCallCallback;
import me.tokarski.webrtc.client.wrappers.GetUserMediaUtils;
import me.tokarski.webrtc.client.wrappers.MediaStream;
import me.tokarski.webrtc.client.wrappers.Utils;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.media.client.Video;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.sencha.gxt.widget.core.client.box.AlertMessageBox;
import com.sencha.gxt.widget.core.client.box.PromptMessageBox;
import com.sencha.gxt.widget.core.client.event.HideEvent;
import com.sencha.gxt.widget.core.client.event.HideEvent.HideHandler;
import com.sencha.gxt.widget.core.client.info.Info;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class WebRTC_GWT implements EntryPoint {
	String wsServerUrl;
	WSConnection connection;
	MediaStream localStream;
	Video localVideoElement;
	boolean localVideoInitialized = false;
	boolean sigChannelConnected = false;
	PeopleWindow peopleWindow;

	static final String WS_CONN_ERROR_MSG = "It looks like the websockets server is down.<br><a href=\"mailto:maciek@tokarski.me\">E-mail me</a> to let me know about this";

	private void connectToSigServer() {
		connection = new WSConnection(wsServerUrl,
				new WSConnection.WSConnectionStateCallback() {

					@Override
					public void onConnectionOpened() {
						sigChannelConnected = true;
						Info.display("", "Websocket connection opened");
						showLoginBox();

					}

					@Override
					public void onConnectionClosed() {
						sigChannelConnected = false;
						Info.display("",
								"Websocket connection closed (server error??)");
					}

					@Override
					public void onCallUnrelatedMessage(JSONObject jso) {
						Utils.consoleLog("onCallUnrelatedMessage");
						Utils.consoleDebug(jso.getJavaScriptObject());
						if (jso.containsKey("command")) {
							String cmd = ((JSONString) jso.get("command"))
									.stringValue();
							if (cmd.equals("register_response")) {
								process_register_response_msg(jso);
							}
							if (cmd.equals("subscriber_state_update")) {
								process_subscriber_state_update(jso);
							}
							if (cmd.equals("call_request")) {
								process_call_request(jso);
							}
						}

					}
				});
	}

	protected void process_call_request(JSONObject jso) {
		String caller = ((JSONString)jso.get("caller")).stringValue();
		Video remotVideo = Video.createIfSupported();
		remotVideo.setAutoplay(true);
		remotVideo.setPoster("facebook-no-face.gif");
		VerticalPanel container = new VerticalPanel();
		container.setSize("100%", "100%");
		container.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
		remotVideo.setSize("100%", "100%");

		Button hangup = new Button("End call");

		final com.sencha.gxt.widget.core.client.Window callWindow = new com.sencha.gxt.widget.core.client.Window();
		callWindow.setHeadingText("Call from " + caller);
		container.add(remotVideo);
		container.add(hangup);
		callWindow.add(container);
		callWindow.show();
		CallStateListener l = new CallStateListener() {
			@Override
			public void onCallTerminate(String cause) {
				callWindow.hide();
			}
			@Override
			public void onCallStarted() {
			}
			@Override
			public void onCallProceding() {
			}
		};
		Integer callId = (int) ((JSONNumber) jso.get("call_id")).doubleValue();
		connection.send(WSMsgsBuilder.confirmCallMsg(callId));
		final Call call =new Call(Call.Direction.IN, localStream, connection, remotVideo, l, callId);
		hangup.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				call.hangup();
				
			}
		});

	}

	protected void process_subscriber_state_update(JSONObject jso) {
		String status = ((JSONString) jso.get("status")).stringValue();
		String nick = ((JSONString) jso.get("nickname")).stringValue();
		if (status.equals("remove")) {
			peopleWindow.remove(nick);
		} else {
			peopleWindow.updateState(nick, status);
		}

	}

	protected void process_register_response_msg(JSONObject jso) {
		String status = ((JSONString) jso.get("status")).stringValue();
		Utils.consoleLog("Got login response with status " + status);
		if (status.equals("success")) {
			String name = ((JSONString) jso.get("name")).stringValue();
			Info.display("", "Welcome " + name + "!");
			JSONObject people = (JSONObject) jso.get("people");
			showPeopleWindow(people);
			com.sencha.gxt.widget.core.client.Window whoAmI = new com.sencha.gxt.widget.core.client.Window();
			whoAmI.add(new HTML("Logged as<br><b>"+name+"</b>" ));
			whoAmI.setPosition(0, 0);
			whoAmI.setClosable(false);
			whoAmI.setResizable(false);
			whoAmI.show();
		}
		if (status.equals("failed")) {
			String reason = ((JSONString) jso.get("reason")).stringValue();
			AlertMessageBox box = new AlertMessageBox("Login error!",
					"Login failed for reason " + reason);
			box.addHideHandler(new HideHandler() {
				@Override
				public void onHide(HideEvent event) {
					showLoginBox();
				}
			});
			box.show();
		}

	}

	private void showPeopleWindow(JSONObject people) {
		PeopleCallCallback callback = new PeopleCallCallback() {

			@Override
			public void call(String who) {
				doCall(who);

			}
		};
		peopleWindow = new PeopleWindow(people, callback);
		peopleWindow.show();

	}

	protected void doCall(String who) {
		Video remotVideo = Video.createIfSupported();
		remotVideo.setAutoplay(true);
		remotVideo.setPoster("facebook-no-face.gif");
		VerticalPanel container = new VerticalPanel();
		container.setSize("100%", "100%");
		container.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
		remotVideo.setSize("100%", "100%");

		Button hangup = new Button("End call");
		

		final com.sencha.gxt.widget.core.client.Window callWindow = new com.sencha.gxt.widget.core.client.Window();
		callWindow.setHeadingText("Call to " + who);
		container.add(remotVideo);
		container.add(hangup);
		callWindow.add(container);
		callWindow.show();
		CallStateListener listener = new CallStateListener() {

			@Override
			public void onCallTerminate(String cause) {
				callWindow.hide();

			}

			@Override
			public void onCallStarted() {
				// TODO Auto-generated method stub

			}

			@Override
			public void onCallProceding() {
				// TODO Auto-generated method stub

			}
		};
		final Call call = new Call(Direction.OUT, localStream, connection,
				remotVideo, listener, who);
		hangup.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				call.hangup();
				
			}
		});
	}

	private void showLocalVideoWnd() {
		com.sencha.gxt.widget.core.client.Window localVideoWindow = new com.sencha.gxt.widget.core.client.Window();
		localVideoWindow.setPixelSize(320, 190);
		localVideoWindow.setModal(false);
		localVideoWindow.setHeadingText("Local video preview");
		localVideoWindow.add(localVideoElement);
		localVideoWindow.setClosable(false);
		localVideoWindow.show();
		localVideoElement.setSize("100%", "100%");
		localVideoWindow.setPagePosition(0, Window.getClientHeight() - 190);
	}

	@Override
	public void onModuleLoad() {
		localVideoElement = Video.createIfSupported();
		if (localVideoElement == null) {
			Window.alert("Your browser does not support getUserMedia()");
			return;
		}
		localVideoElement.setPoster("facebook-no-face.gif");
		localVideoElement.setAutoplay(true);
		getUserMedia();


		wsServerUrl = Window.Location.getParameter("sigUrl");
		WSServerCheck.checkServerAlive(wsServerUrl,
				new WSServerCheck.CheckCallback() {
					@Override
					public void serverDead() {
						showAlertMessageAndReload("Websocket server error",
								WS_CONN_ERROR_MSG);
					}

					@Override
					public void serverAlive() {
						connectToSigServer();
						Info.display("",
								"Websockets server is alive, connecting");
					}
				});

	}

	public void getUserMedia() {
		GetUserMediaUtils.getUserMedia(true, true,
				new GetUserMediaUtils.GetUserMedaCallback() {

					@Override
					public void navigatorUserMediaSuccessCallback(
							MediaStream mediaStream) {
						localVideoElement.addSource(mediaStream
								.createMediaObjectBlobUrl());
						localStream = mediaStream;
						localVideoInitialized = true;
						showLocalVideoWnd();

					}

					@Override
					public void navigatorUserMediaErrorCallback(
							JavaScriptObject error) {
						AlertMessageBox alertBox = new AlertMessageBox(
								"Camera error",
								"Please provide access to your cam and mic");
						alertBox.addHideHandler(new HideHandler() {
							@Override
							public void onHide(HideEvent event) {
								getUserMedia();
							}
						});
						alertBox.show();
						Utils.consoleDebug(error);

					}
				});
	}

	private void showAlertMessageAndReload(String title, String message) {
		AlertMessageBox alertBox = new AlertMessageBox(title, message);
		alertBox.addHideHandler(new HideHandler() {

			@Override
			public void onHide(HideEvent event) {
				Window.Location.reload();

			}
		});
		alertBox.show();
	}

	private void showLoginBox() {
		final PromptMessageBox box = new PromptMessageBox("Name",
				"Please enter your name:");
		box.addHideHandler(new HideHandler() {
			@Override
			public void onHide(HideEvent event) {
				if (box.getValue() == null) {
					showLoginBox();
					return;
				}
				connection.send(WSMsgsBuilder.registrationMsg(box.getValue()));

			}
		});
		box.show();
	}

}