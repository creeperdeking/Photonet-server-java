package com.pgx.java.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import com.google.gson.*;
import java.sql.*;

@ServerEndpoint("/endpoint")
public class MyWebSocket {

	public Connection dbConnection;
	public String userLogin;
	public String idAlbu;

	public MyWebSocket() {
		log("Version 1.5");
		this.userLogin = "";
		this.idAlbu = "";

		try {
			// Connexion à la base de donnée
			dbConnection = DriverManager.getConnection("jdbc:mysql://localhost/photonet", "root", "Base2Donn&e");
		} catch (SQLException ex) {
			error("SQLException: " + ex.getMessage());
			error("SQLState: " + ex.getSQLState());
			error("VendorError: " + ex.getErrorCode());
		}
	}

	@OnOpen
	public void onOpen(Session session) {
		log("OnOpen id=" + session.getId());

		// Permet de traiter les paramètres de l'url
		//Map<String, List<String>> params = session.getRequestParameterMap();
	}

	@OnClose
	public void onClose(Session session) {
		log("OnClose id=" + session.getId());
	}
	
	// Envoie un message préformaté
	private void sendMessage(Session s, String type, String action, String arguments, String idAlbu) {
		String virgule = "";
		if (!arguments.equals("")) {
			virgule = ", ";
		}
		sendMessage(s, " {\"type\": \"" + type + "\", \"data\": {\"action\": \"" + action + "\", \"idalbu\":"
				+ idAlbu + virgule + arguments + "}} ");
	}
	
	// envoie un message sans se soucier de idalbu
	private void sendMessage(Session s, String type, String action, String arguments) {
		sendMessage(s, type, action, arguments, "0");
	}
	
	// Envoie un message brut
	private void sendMessage(Session s, String message) {
		log("Envoi du message suivant au client id=" + s.getId());
		log(message);
		try {
			s.getBasicRemote().sendText(message);
		} catch (IOException e) {
			error("Arg! On a pas pu envoyer le message suivant: " + message);
			e.printStackTrace();
		}
	}
	
	// Fonction faire pour les sélect, renvoie les résultats
	private ResultSet sqlQuerySelect(String query) {
		log(query);
		ResultSet result = null;
		try {
			Statement myStatement = dbConnection.createStatement();
			result = myStatement.executeQuery(query);
		} catch (SQLException ex) {
			error("SQLException: " + ex.getMessage());
			error("SQLState: " + ex.getSQLState());
			error("VendorError: " + ex.getErrorCode());
		}
		return result;
	}
	
	// Fonction faite pour les query SQL qui ne renvoient rien
	private void sqlQuery(String query) {
		log(query);
		try {
			Statement myStatement = dbConnection.createStatement();
			myStatement.executeUpdate(query);
		} catch (SQLException ex) {
			error("SQLException: " + ex.getMessage());
			error("SQLState: " + ex.getSQLState());
			error("VendorError: " + ex.getErrorCode());
		}
	}

	public void log(String message) {
		System.out.println(message);
	}

	// Les warning sont écrit pour toute action qui risque fortement de créer une
	// erreur ou un bug quelque part
	public void warning(String message) {
		System.out.println("/!\\ Attention /!\\ : " + message);
	}

	// Les warning sont écrits lors de l'échec d'une instruction qui devrait réussir
	public void error(String message) {
		System.out.println("----- ERREUR ----- : " + message);
	}
	
	// Donne un sous élément d'un objet Json
	public JsonElement getJsonObject(JsonObject obj, String elem) {
		try {
			return obj.get(elem);
		} catch (Exception ex) {
			error("Impossible de récupérer l'élément " + elem + " d'un objet JSON");
			error(ex.getMessage());
			return null;
		}
	}
	
	// Donne un sous élément en String d'un objet Json
	public String getJsonString(JsonObject obj, String elem) {
		try {
			return getJsonObject(obj, elem).getAsString();
		} catch (Exception ex) {
			error("Impossible de convertir l'élement " + elem + " d'un objet JSON en string");
			error(ex.getMessage());
			return null;
		}
	}

	// Nous indiquent si l'utilisateur actuel peut avoir accès à cet album
	public boolean canModifyAlbum(String idAlbu) {
		return canModifyAlbum(this.userLogin, idAlbu);
	}
	
	// Si login est le propriétaire ou un collaborateur dans idAlbu
	public boolean canModifyAlbum(String login, String idAlbu) {
		String query1 = "select * from Album where (proprietaire = \""+login+ "\" and idalbu = "+idAlbu+")";
		String query2 = "select * from Partage where (idutil = \""+login+"\" and idalbu = "+idAlbu+")";
		ResultSet result1 = sqlQuerySelect(query1);
		ResultSet result2 = sqlQuerySelect(query2);
		try {
			return result1.next() || result2.next();
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	// Nous indique si l'utilisateur est propriétaire de cet album
	public boolean isMyAlbum(String idAlbu) {
		return isMyAlbum(this.userLogin, idAlbu);
	}
	
	// Si login est le propriétaire d'idAlbu
	public boolean isMyAlbum(String login, String idAlbu) {
		String query1 = "select * from Album where (proprietaire = \""+login+ "\" and idalbu = "+idAlbu+")";
		ResultSet result1 = sqlQuerySelect(query1);
		try {
			return result1.next();
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	// Envoie le message à tous les utilisateurs connectés qui ont le droit de modifier l'album idAlbu
	public void sendToAllGranted(Session session, String message, String idAlbu) {
		for (Session s : session.getOpenSessions()) {
			String login = (String) s.getUserProperties().get("login");
			if (login != null) {
				if (canModifyAlbum(login, idAlbu) && s.isOpen()) {
					sendMessage(s, message);
				}
			} else {
				warning("Impossible de récupérer le login de la session "+ s.getId());
			}
		}
	}

	// Envoie le message à tous les utilisateurs connectés qui ont le droit de modifier l'album idAlbu
	public void sendToAllGranted(Session session, String type, String action, String arguments, String idAlbu) {
		for (Session s : session.getOpenSessions()) {
			String login = (String) s.getUserProperties().get("login");
			if (login != null) {
				if (canModifyAlbum(login, idAlbu) && s.isOpen()) {
					sendMessage(s, type, action, arguments, idAlbu);
				}
			} else {
				warning("Impossible de récupérer le login de la session "+ s.getId());
			}
		}
	}
	
	// Donne le nom de l'album
	public String getAlbumName(String idAlbum) {
		String query = "select nom from Album where idalbu="+idAlbum;
		ResultSet mesResultats = sqlQuerySelect(query);
		String albumName = null;
		try {
			if (mesResultats.next()) {
				albumName = mesResultats.getString("nom");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return albumName;
	}
	
	// Donne le nombre de collaborateur (incluant le propriétaire)
	public Integer getNbSharing(String idAlbum) {
		// We get the number of user that have the right to edit this album:
		String query10 = "select count(*) as count from Partage where idalbu=" + idAlbum;
		ResultSet mesResultats3 = sqlQuerySelect(query10);
		Integer nbSharing = null;
		try {
			mesResultats3.next();
			nbSharing = mesResultats3.getInt("count") + 1;
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return nbSharing;
	}

	@OnMessage
	public void onMessage(String message, Session session) {
		log("@OnMessage id=" + session.getId());
		log(message);

		JsonObject jsonObject = new JsonParser().parse(message).getAsJsonObject();
		JsonObject jsonObjectData = null;
		try {
			jsonObjectData = jsonObject.getAsJsonObject("data");
		} catch (Exception ex) {
			error("Impossible de récupérer l'élément data d'un objet JSON");
			error(ex.getMessage());
			return;
		}

		String type = getJsonString(jsonObject, "type");
		// Note: on le récupère en String pour éviter des casts, mais idAlbu est un entier
		this.idAlbu = getJsonString(jsonObjectData, "idalbu");

		//Gestion des droits:
		log("Vérification des droits de l'utilisateur");
		// Si on ne s'est pas encore loggé et que le message n'est pas un message de login, on ne traite pas le message
		if (this.userLogin == "" && !type.equals("identification")) {
			warning("Le client d'id " + session.getId() + " a tenté de faire des actions sans avoir pu se connecter");
			return;
		}
		// Si le message a à voir avec le tableau de bord ou l'identification, on a le droit de faire toutes les actions
		boolean hasTheRights = (type.equals("album") || type.equals("identification"));
		if (!hasTheRights) { // Sinon on vérifie que l'utilisateur a le droit d'agir sur cet album
			hasTheRights = canModifyAlbum(this.idAlbu);
		}
		if (!hasTheRights) {
		  warning("Le client " + this.userLogin + " a tenté de demander l'accès à l'album d'id "+ this.idAlbu+ " alors qu'il n'en avait pas les droits");
		  return;
		}

		switch (type) {
		case "refresh_canvas": // Rafraichissement d'une double page
			log("Send page to the client");

			sendMessage(session, "clean_page", "", "");

			String currentpage = getJsonString(jsonObjectData, "page");
			boolean canAccess = canModifyAlbum(this.idAlbu);
			if (!canAccess) {
				break;
			}

			String query = "select * from Contenu where idalbu=" + this.idAlbu + " and (numpage = " + currentpage + " or numpage = "
					+ String.valueOf(Integer.parseInt(currentpage) + 1) + ")";
			ResultSet mesResultats = sqlQuerySelect(query);

			try {
				int nb_element = 0;
				while (mesResultats.next()) {
					String arguments = " \"posx\":" + mesResultats.getString("posX") + "," + " \"posy\":"
							+ mesResultats.getString("posY") + "," + " \"scaleX\":" + mesResultats.getString("scaleX")
							+ "," + " \"scaleY\":" + mesResultats.getString("scaleY") + "," + " \"angle\":"
							+ mesResultats.getString("angle") + "," + "\"id\":" + mesResultats.getString("idcontenu")
							+ "," + "\"name\":\"" + mesResultats.getString("pathcontenu") + "\"," + "\"page\":"
							+ mesResultats.getString("numpage");

					String content_type = mesResultats.getString("typec");
					if (content_type.equals("image")) {
						sendMessage(session, "content", "create_image", arguments, this.idAlbu);
						nb_element+=1;
					} else if (content_type.equals("texte")) {
						sendMessage(session, "content", "create_text", arguments, this.idAlbu);
						nb_element+=1;
					} else {
						warning("Problème dans la base de donnée: impossible de trouver le type " + content_type);
					}
				}
				String querylock1 = "select verrou from Pages where idalbu=" + this.idAlbu + "  and numpage = " + currentpage;
				ResultSet mesResultats2 = sqlQuerySelect(querylock1);
				mesResultats2.next();
				sendMessage(session, "status_page", mesResultats2.getString("verrou"),
						"\"page\" : \"" + currentpage + "\"", this.idAlbu);
				sendMessage(session, "end_reload", "", "\"nb\":"+nb_element);
			} catch (SQLException e) {
				error("Erreur sql dans get_page");
				e.printStackTrace();
			}
			break;
		case "status_page": // Gestion du lock
			log("update page's lock and propagate");
			String verrou = getJsonString(jsonObjectData, "action");
			String numpage = getJsonString(jsonObjectData, "page");

			if (isMyAlbum(idAlbu)) {// Test si on est l'owner de l'album
				String querylock2 = "update Pages set verrou = " + verrou + " where idalbu = " + this.idAlbu + " and numpage = " + numpage;
				sqlQuery(querylock2);
				sendToAllGranted(session, message, this.idAlbu);
			}

			break;
		case "refresh_bank": // Rafraichissement de la banque d'image
			log("Send image bank to the client");

			String queryimg = "select * from Photo where idalbu=" + this.idAlbu;
			ResultSet mesResultatsimg = sqlQuerySelect(queryimg);

			try {
				while (mesResultatsimg.next()) {
					String argument = " \"path\":" + mesResultatsimg.getString("pathphoto");
					sendMessage(session, "bank", "add_image", argument, this.idAlbu);
				}
				sendMessage(session, "end_refresh", "", "", this.idAlbu);
			} catch (SQLException e) {
				error("Erreur sql dans get_image");
				e.printStackTrace();
			}
			break;
		case "message": // Si c'est un message du chat
			log("Envoie le message à tous les autres clients...");
			sendToAllGranted(session, message, this.idAlbu);// On renvoie le message à tous
			break;
		case "upload": // Téléchargement d'une image dans le répertoire d'image
			String path = getJsonString(jsonObjectData, "path");
			String query6 = "insert into Photo" + " values (\"" + path + "\"," + this.idAlbu + ",'" + this.userLogin +"')";

			sqlQuery(query6);
			// On renvoie le message à tous ceux qui y ont droit y compris l'envoyeur
			sendToAllGranted(session, "bank", "add_image", "\"path\" : " + path, this.idAlbu);
			break;
		case "content":
			log("Mise à jour de la base de donnée et envoie du changement à tous les clients");
			String action = getJsonString(jsonObjectData, "action");
			String id = null;
			switch (action) {
			case "create_image": // Création d'une image
			case "create_text": // création d'un texte
				// On crée l'id
				String queryid = "select max(idcontenu)+1 as id from Contenu where idalbu = "+this.idAlbu+" and numpage = "+getJsonString(jsonObjectData, "page");
				try {
					ResultSet mesResultatsid = sqlQuerySelect(queryid);
					if (mesResultatsid.next()) {
						id = mesResultatsid.getString("id");
					}
				} catch (SQLException e) {
					error("Erreur sql dans get_image");
					e.printStackTrace();
				}
			case "update_image": // Modification d'une image
			case "update_text": // Modification d'un texte
				if (id==null) {
					id = getJsonString(jsonObjectData, "id");
				}
				String page = getJsonString(jsonObjectData, "page");
				String angle = getJsonString(jsonObjectData, "angle");
				String scaleX = getJsonString(jsonObjectData, "scaleX");
				String scaleY = getJsonString(jsonObjectData, "scaleY");
				String posx = getJsonString(jsonObjectData, "posx");
				String posy = getJsonString(jsonObjectData, "posy");
				String name = getJsonString(jsonObjectData, "name");

				String typec = "texte";
				if (action.equals("update_image") || action.equals("create_image")) {
					typec = "image";
				}
				String query3 = "insert into Contenu (idcontenu, idalbu, numpage, pathcontenu, posX, posY, scaleX, scaleY, angle, typec)"
						+ " values (" + id + "," + this.idAlbu + "," + page + "," + "\"" + name + "\"," + posx + "," + posy + ","
						+ scaleX + "," + scaleY + "," + angle + ",\"" + typec + "\")" + " ON DUPLICATE KEY UPDATE "
						+ "posX =" + posx + "," + "posY =" + posy + "," + "pathcontenu = \"" + name + "\"," + "scaleX ="
						+ scaleX + "," + "scaleY =" + scaleY + "," + "angle =" + angle + ", typec = \"" + typec + "\"";

				sqlQuery(query3);

				//On met à jour l'id dans le message json et on envoi ce message à tout le monde
				String arguments = " \"posx\":" + posx + "," + " \"posy\":"
						+ posy + "," + " \"scaleX\":" + scaleX
						+ "," + " \"scaleY\":" + scaleY + "," + " \"angle\":"
						+ angle + "," + "\"id\":" + id
						+ "," + "\"name\":\"" + name + "\"," + "\"page\":"
						+ page;

				String content_type = typec;
				//(Session session, String type, String action, String arguments, String idAlbu, boolean backToSender)
				if (content_type.equals("image")) {
					sendToAllGranted(session, type, action, arguments, this.idAlbu);
				} else if (content_type.equals("texte")) {
					sendToAllGranted(session, type, action, arguments, this.idAlbu);
				} else {
					warning("Problème dans la base de donnée: impossible de trouver le type " + content_type);
				}

				break;
			case "remove": // Supprime un émlément
				log("delete change in db and send it to all the other clients...");
				String query2 = "delete from Contenu where " + "idcontenu = " + getJsonString(jsonObjectData, "id")
						+ " and " + "idalbu = " + this.idAlbu + " and " + "numpage = " + getJsonString(jsonObjectData, "page");
				sqlQuery(query2);
				// On renvoie le message à tous sauf à l'envoyeur
				sendToAllGranted(session, message, this.idAlbu);
				break;
			default:
				warning("!L'action " + action + " n'est pas reconnue");
				break;
			}
			break;
		case "select": // Message de type sélection d'élément sur un album
			String spage = getJsonString(jsonObjectData, "page");
			String sid = getJsonString(jsonObjectData, "id");
			String sselected = getJsonString(jsonObjectData, "selected");
			String arguments = "\"login\" : \"" + this.userLogin+"\""+",\"page\" : " + spage+","+"\"id\" : " + sid+","+"\"selected\" : \"" + sselected+"\"";
			sendToAllGranted(session, "select", "", arguments, this.idAlbu);
			break;
		case "identification": // Message de type authentification, création de compte, connexion à un album
			log("Reçu un message de type identification");
			String action2 = getJsonString(jsonObjectData, "action");
			switch (action2) {
			case "login": // Connexion
				String login = getJsonString(jsonObjectData, "login");
				String query4 = "Select * from Utilisateur where nom ='" + login + "' and mdp ='"
						+ getJsonString(jsonObjectData, "password") + "'";

				ResultSet resultQuery4 = sqlQuerySelect(query4);

				try {
					if (resultQuery4.next()) {
						log("Mot de passe correct");
						this.userLogin = login;
						session.getUserProperties().put("login", login);
						sendMessage(session, "identification", "good_login", "");
					} else {
						log("Mot de passe incorrect");
						sendMessage(session, "identification", "bad_login", "");
					}
				} catch (SQLException e) {
					error("Erreur sql dans login");
					e.printStackTrace();
				}
				break;
			case "register": // Création de compte
				log("Création d'un nouvel utilisateur");

				String query5 = "INSERT INTO Utilisateur (idutil, nom,mdp) VALUES ('"
						+ getJsonString(jsonObjectData, "login") + "', '" + getJsonString(jsonObjectData, "login")
						+ "', '" + getJsonString(jsonObjectData, "password") + "')";
				try {
					Statement myStatement = dbConnection.createStatement();
					myStatement.executeUpdate(query5);
					sendMessage(session, "identification", "good_register", "");
				} catch (SQLException ex) { // Si la personne n'a pas pu être créé, on envoie un update
					error("personne non créée");
					sendMessage(session, "identification", "bad_register", "");
					error("SQLException: " + ex.getMessage());
					error("SQLState: " + ex.getSQLState());
					error("VendorError: " + ex.getErrorCode());
				}
				break;
			case "album": // Si on doit se connecter à un album
				log("Demande de connexion à un album");
				String idAlbum = getJsonString(jsonObjectData, "idalbu");
				boolean canModifyAlbumOK = canModifyAlbum(idAlbum);
				if (canModifyAlbumOK) {
					this.idAlbu = idAlbum;
					sendMessage(session, "identification", "good_album", "");
				} else {
					sendMessage(session, "identification", "bad_album", "");
				}
				break;
			default:
				warning("Action inconnue: " + action2);
				break;
			}
			break;
		case "album": // Si c'est un message qui a un rapport avec la gestion des albums
			log("Action sur l'album");
			String action3 = getJsonString(jsonObjectData, "action");
			switch (action3) {
			case "get_my_albums": // donne la liste de tous les albums qui m'appartiennent
				log("Envoi des albums au client");

				String query7 = "select * from Album where proprietaire='" + this.userLogin + "'";
				ResultSet mesResultats2 = sqlQuerySelect(query7);

				try {
					while (mesResultats2.next()) {
						String idalbum = mesResultats2.getString("idalbu");
						String albumName = mesResultats2.getString("nom");

						// We get the number of user that have the right to edit this album:
						String query10 = "select count(*) as count from Partage where idalbu=" + idalbum;
						ResultSet mesResultats3 = sqlQuerySelect(query10);
						mesResultats3.next();
						int nbSharing = mesResultats3.getInt("count") + 1;
						String arguments1 = " \"name\":\"" + albumName + "\", \"editors_nb\":" + nbSharing;

						sendMessage(session, "album", "my_album", arguments1, idalbum);
					}
				} catch (SQLException e) {
					error("Erreur sql dans get_page");
					e.printStackTrace();
				}
				break;
			case "get_my_shared_albums": // Donne la liste des albums que je peux éditer mais dont je ne suis pas le proporiétaire
				log("Envoi des albums qu'on a partagé au client");

				String query17 = "select a.nom as nom, a.idalbu as idalbu from Partage as p,"
						+ " Album as a where p.idalbu=a.idalbu and p.idutil='"+ this.userLogin + "'";
				ResultSet mesResultats6 = sqlQuerySelect(query17);

				try {
					while (mesResultats6.next()) {
						String idalbum = mesResultats6.getString("idalbu");
						String albumName = mesResultats6.getString("nom");
						int nbSharing = getNbSharing(idalbum);
						String arguments1 = " \"name\":\"" + albumName + "\", \"editors_nb\":" + nbSharing;

						sendMessage(session, "album", "my_shared_album", arguments1, idalbum);
					}
				} catch (SQLException e) {
					error("Erreur sql dans get_page");
					e.printStackTrace();
				}
				break;
			case "get_my_invitations": // Envoie toutes les invitations qu'on a reçu
				log("Envoi des albums auquel on demande au client de participer");

				String query18 = "select a.nom as nom, a.idalbu as idalbu, i.demandeur as demandeur from Invitation as i, "
						+ "Album as a where i.idalbu=a.idalbu and i.invite='" + this.userLogin + "' and i.demandeur=a.proprietaire";

				ResultSet mesResultats7 = sqlQuerySelect(query18);

				try {
					while (mesResultats7.next()) {
						String idalbum = mesResultats7.getString("idalbu");
						String albumName = mesResultats7.getString("nom");
						String origin = mesResultats7.getString("demandeur");
						String arguments1 = " \"name\":\"" + albumName + "\", \"origin\":\""+origin+"\"";

						sendMessage(session, "album", "my_invitation", arguments1, idalbum);
					}
				} catch (SQLException e) {
					error("Erreur sql dans get_page");
					e.printStackTrace();
				}
				break;
			case "accept_invitation": // Accepte une invitation
				log("Acceptation de l'invitation");

				// Vérification de l'existence de l'invitation:
				String query19 = "select * from Invitation as i" + " where i.invite='" + this.userLogin + "' "
						+ "and i.idalbu="+idAlbu;
				ResultSet mesResultats8 = sqlQuerySelect(query19);
				boolean invitation_exist = false;
				try {
					invitation_exist = mesResultats8.next();
				} catch (SQLException e2) {
					e2.printStackTrace();
				}
				// Vérification de l'existence de l'invitation et des droits de l'utilisateur
				if (!canModifyAlbum(idAlbu) && invitation_exist) {
					String query20 = "insert into Partage values('" + this.userLogin + "', "+idAlbu+")";
					String queryDeleteInvitation = "delete from Invitation where idalbu=" + idAlbu + " and invite='" + this.userLogin + "'";
					sqlQuery(query20);
					sqlQuery(queryDeleteInvitation);

					String arguments1 = " \"name\":\"" + getAlbumName(idAlbu) + "\", \"editors_nb\":" + getNbSharing(idAlbu);
					String argumentsRemoveInvitation = "\"guest\":\"" + this.userLogin+"\"";

					sendMessage(session, "album", "my_shared_album", arguments1, idAlbu);
					sendMessage(session, "album", "remove_invitation", argumentsRemoveInvitation, idAlbu);
				}
				break;
			case "refuse_invitation": // Refuse une invitation
				log("Refus de l'invitation");

				// Vérification de l'existence de l'invitation:
				String query21 = "select * from Invitation as i" + " where i.invite='" + this.userLogin + "' "
						+ "and i.idalbu="+idAlbu+"";
				ResultSet mesResultats9 = sqlQuerySelect(query21);
				boolean invitation_exist1 = false;
				try {
					invitation_exist1 = mesResultats9.next();
				} catch (SQLException e2) {
					e2.printStackTrace();
				}

				if (!canModifyAlbum(idAlbu) && invitation_exist1) {// Vérification de l'existence de l'invitation et des droits de l'utilisateur
					String query22 = "delete from Invitation where invite='" + this.userLogin + "' and idalbu="+idAlbu;
					sqlQuery(query22);
					String argumentsRemoveInvitation = "\"guest\":\"" + this.userLogin+"\"";
					sendMessage(session, "album", "remove_invitation", argumentsRemoveInvitation, idAlbu);
				}
				break;
			case "cancel_pending_invitation": // Annule une invitation
				log("Refus de l'invitation en cours");
				String guest2 = getJsonString(jsonObjectData, "guest");
				// Vérification de l'existence de l'invitation:
				String query25 = "select * from Invitation as i" + " where i.invite='" + guest2 + "' "
						+ "and i.idalbu="+idAlbu+"";
				ResultSet mesResultats12 = sqlQuerySelect(query25);
				boolean invitation_exist2 = false;
				try {
					invitation_exist2 = mesResultats12.next();
				} catch (SQLException e2) {
					e2.printStackTrace();
				}
				log(String.valueOf(invitation_exist2));

				if (canModifyAlbum(idAlbu) && invitation_exist2) {// Vérification de l'existence de l'invitation et des droits de l'utilisateur
					String query22 = "delete from Invitation where invite='" + guest2 + "' and idalbu="+idAlbu;
					sqlQuery(query22);
					String argumentsRemoveInvitation = "\"guest\":\"" + guest2 +"\"";
					sendMessage(session, "album", "remove_invitation", argumentsRemoveInvitation, idAlbu);
				}
				break;
			case "get_my_suggested_invitations": // Donner la liste des invitations qu'on me propose de faire
				log("Envoi des invitations qu'on propose au client de faire");

				String query23 = "select a.nom as nom, a.idalbu as idalbu, i.demandeur as demandeur, i.invite as invite from Invitation as i, "
						+ "Album as a where i.idalbu=a.idalbu and a.proprietaire = '"+this.userLogin+"' and i.demandeur != '"+userLogin+"'";

				ResultSet mesResultats10 = sqlQuerySelect(query23);

				try {
					while (mesResultats10.next()) {
						String idalbum = mesResultats10.getString("idalbu");
						String albumName = mesResultats10.getString("nom");
						String origin = mesResultats10.getString("demandeur");
						String guest = mesResultats10.getString("invite");
						String arguments1 = " \"name\":\"" + albumName + "\", \"guest\":\""+guest+"\", \"origin\":\""+origin+"\"";

						sendMessage(session, "album", "suggested_invitation", arguments1, idalbum);
					}
				} catch (SQLException e) {
					error("Erreur sql dans get_page");
					e.printStackTrace();
				}
				break;
			case "get_pending_invitations": // Donner la liste des invitations en attente
				log("Envoi des invitations que le client a fait mais qui ne sont pas encore acceptées");

				String query24 = "select idalbu, invite from Invitation "
						+ "where demandeur = '"+userLogin+"'";

				ResultSet mesResultats11 = sqlQuerySelect(query24);

				try {
					while (mesResultats11.next()) {
						String idalbum = mesResultats11.getString("idalbu");
						String albumName = getAlbumName(idalbum);
						String guest = mesResultats11.getString("invite");
						String arguments1 = " \"name\":\"" + albumName + "\", \"guest\":\""+guest+"\"";
						sendMessage(session, "album", "my_pending_invitation", arguments1, idalbum);
					}
				} catch (SQLException e) {
					error("Erreur sql dans get_page");
					e.printStackTrace();
				}
				break;
			case "create_album": // Créer un album
				log("Création d'un nouvel album");

				String query8 = "INSERT INTO Album (nom, proprietaire, nbpage, nbpixX, nbpixY) VALUES ('"
						+ getJsonString(jsonObjectData, "name") + "', '" + this.userLogin + "', " + "20" + ", " + "300"
						+ ", " + "700" + ")";
				log(query8);

				try {
					Statement myStatement = dbConnection.createStatement();
					myStatement.executeUpdate(query8);
					String query9 = "select max(idalbu) as idalbu  from Album where proprietaire='" + this.userLogin + "'";
					ResultSet mesResultats3 = sqlQuerySelect(query9);

					mesResultats3.next();
					sendMessage(session, "album", "good_creation", "", mesResultats3.getString("idalbu"));
				} catch (SQLException ex) { // Si la personne n'a pas pu être créé, on envoie un update
					error("Album non créée");
					sendMessage(session, "album", "bad_creation", "");
					error("SQLException: " + ex.getMessage());
					error("SQLState: " + ex.getSQLState());
					error("VendorError: " + ex.getErrorCode());
				}
				break;
			case "get_info_album": // Donner les infos sur l'album
				String query11 = "select proprietaire, nom from Album where idalbu=" + this.idAlbu;
				ResultSet mesResultats3 = sqlQuerySelect(query11);

				try {
					mesResultats3.next();
					sendMessage(session, "album", "owner", "\"login\": \""+mesResultats3.getString("proprietaire")+"\"", this.idAlbu);
					sendMessage(session, "album", "album_name", "\"name\": \""+mesResultats3.getString("nom")+"\"", this.idAlbu);
				} catch (SQLException e) {
					e.printStackTrace();
				}

				break;
			case "delete_collabo": // Supprimer les droits d'un collaborateur sur un album
				String login = getJsonString(jsonObjectData, "login");
				String query14 = "delete from Partage where idalbu=" + this.idAlbu
					+ " and idutil=\""+ login +"\"";
				sqlQuery(query14);
				sendMessage(session, "album", "remove_collabo", "\"login\":\""+login+"\"", this.idAlbu);
				break;
			case "refuse_invitation_proposal": // Refuser une proposition d'invitation pour un autre
				if (isMyAlbum(idAlbu)) {
					String guest = getJsonString(jsonObjectData, "guest");
					String queryDeleteInvitation = "delete from Invitation where invite='"+ guest + "' and idalbu="+idAlbu;
					sqlQuery(queryDeleteInvitation);

					String argumentsRemoveInvite = "\"guest\":\""+guest+"\"";
					sendMessage(session, "album", "remove_invitation", argumentsRemoveInvite, this.idAlbu);
				}
				break;
			case "accept_invitation_proposal": // Acceptation de la proposition d'une invitation
			case "invite_collabo": // Invitation d'un collaborateur
				String inviteName;
				if (action3.equals("accept_invitation_proposal")) {
					inviteName = getJsonString(jsonObjectData, "guest");
				} else {
					inviteName = getJsonString(jsonObjectData, "login");
				}
				// Vérification que l'utilisateur existe:
				String query16 = "select * from Utilisateur where idutil=\""+ inviteName + "\"";
				ResultSet results = sqlQuerySelect(query16);
				try {
					if (!results.next()) { // Si l'utilisateur en question n'existe pas, on quite le switch
						sendMessage(session, "album", "bad_invite", "", this.idAlbu);
						break;
					}
				} catch (SQLException e1) {
					e1.printStackTrace();
				}

				String query15;
				// Si on accepte une invitation, on fait un update de l'invitation existante
				if (action3.equals("accept_invitation_proposal")) {
					if (isMyAlbum(idAlbu)) {
						query15 = "update Invitation set demandeur='"+userLogin+"' where invite = '"+ inviteName
								+"' and idalbu = '"+ this.idAlbu + "'";
					} else {
						warning("On a demandé l'acceptation d'une invitation sur un album qu'on ne possède pas!");
						break;
					}
				} else {
					query15 = "insert into Invitation values(\""+ inviteName
							+"\", "+ this.idAlbu + ", '"+userLogin+"')";
				}

				log(query15);

				if (canModifyAlbum(userLogin, this.idAlbu)) {
					try {
						Statement myStatement = dbConnection.createStatement();
						myStatement.executeUpdate(query15);
						if (action3 == "accept_invitation_proposal") {
							String argumentsRemoveInvite = "\"guest\":\""+inviteName+"\"";
							sendMessage(session, "album", "remove_invite", argumentsRemoveInvite, this.idAlbu);
						} else {
							sendMessage(session, "album", "good_invite", "", this.idAlbu);
						}

					} catch (SQLException ex) { // Si la personne n'a pas pu être créé, on envoie un update
						error("Impossible d'insérer le collaborateur "+inviteName);
						error("SQLException: " + ex.getMessage());
						error("SQLState: " + ex.getSQLState());
						error("VendorError: " + ex.getErrorCode());
						sendMessage(session, "album", "bad_invite", "", this.idAlbu);
					}
				} else {
					sendMessage(session, "album", "bad_invite", "", this.idAlbu);
				}
				break;
			case "get_collabos": // On demande la liste des collaborateurs de l'album
				String query12 = "select idutil from Partage where idalbu=" + this.idAlbu;
				String query13 = "select proprietaire from Album where idalbu=" + this.idAlbu;
				// Ajouter le owner
				ResultSet mesResultats4 = sqlQuerySelect(query12);
				ResultSet mesResultats5 = sqlQuerySelect(query13);

				try {
					mesResultats5.next();
					sendMessage(session, "album", "add_collabo", "\"login\": \""+mesResultats5.getString("proprietaire")+"\"", this.idAlbu);
					while(mesResultats4.next()) {
						sendMessage(session, "album", "add_collabo", "\"login\": \""+mesResultats4.getString("idutil")+"\"", this.idAlbu);
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}

				break;
			case "delete_album": // On demane à supprimer un album
				if (isMyAlbum(idAlbu)) {// Teste si on est l'owner de l'album
					String querydelete = "delete from Album where idalbu=\""+idAlbu+"\"";
					sqlQuery(querydelete);
					sendMessage(session, "album", "remove_album", "", this.idAlbu);
				} else {
					boolean canModifyAlbumOK = canModifyAlbum(idAlbu);
					if (canModifyAlbumOK) {
						String querydeletepartage = "delete from Partage where idalbu=\""+idAlbu+"\" and idutil=\""+this.userLogin+"\"";
						sqlQuery(querydeletepartage);
						sendMessage(session, "album", "remove_album", "", this.idAlbu);
					}
				}
				break;
			default:
				warning("Action inconnue: " + action3);
				break;
			}
			break;
		default:
			warning("Type inconnu: " + type);
			break;
		}
	}

	@OnError
	public void onError(Throwable t) {
		try {
			dbConnection.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		error("OnError : " + t.getMessage());
	}
}
