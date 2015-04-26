package controllers

import services._
import services.mongodb.MongoDBProjectService
import services.mongodb.MongoDBInstitutionService
import services.mongodb.MongoDBEventService
import play.api.data.Form
import play.api.data.Forms._
import models.Info
import models.UUID
import models.MiniUser
import models.Event
import play.api.Logger
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date



class Profile @Inject()(users: UserService, institutions: MongoDBInstitutionService, projects: MongoDBProjectService, events: EventService, scheduler: SchedulerService) extends  SecuredController {

  val bioForm = Form(
    mapping(
      "avatarUrl" -> optional(text),
      "biography" -> optional(text),
      "currentprojects" -> list(text),
      "institution" -> optional(text),
      "orcidID" -> optional(text),
      "pastprojects" -> list(text),
      "position" -> optional(text),
      "emailsettings" -> optional(text)
    )(Info.apply)(Info.unapply)
  )

  def editProfile() = SecuredAction() {
    implicit request =>
      implicit val user = request.user
    var avatarUrl: Option[String] = None
    var biography: Option[String] = None
    var currentprojects: List[String] = List.empty
    var institution: Option[String] = None
    var orcidID: Option[String] = None
    var pastprojects: List[String] = List.empty
    var position: Option[String] = None
    var emailsettings: Option[String] = None
    user match {
      case Some(muser) => {
        muser.avatarUrl match {
          case Some(url) => {
            val questionMarkIdx :Int = url.indexOf("?")
            if (questionMarkIdx > -1) {
              avatarUrl = Option(url.substring(0, questionMarkIdx))
            } else {
              avatarUrl = Option(url)
            }
          }
          case None => avatarUrl = None
        }
        muser.biography match {
          case Some(filledOut) => biography = Option(filledOut)
          case None => biography = None
        }
        muser.currentprojects match {
          case x :: xs => currentprojects = x :: xs
          case nil => currentprojects = nil
        }
        muser.institution match {
          case Some(filledOut) => institution = Option(filledOut)
          case None => institution = None
        }
        muser.orcidID match {
          case Some(filledOut) => orcidID = Option(filledOut)
          case None => orcidID = None
        }
        muser.pastprojects match {
          case x :: xs => pastprojects = x :: xs
          case nil => pastprojects = nil
        }
        muser.position match {
          case Some(filledOut) => position = Option(filledOut)
          case None => position = None
        }

        emailsettings = None

        val newbioForm = bioForm.fill(Info(
          avatarUrl,
          biography,
          currentprojects,
          institution,
          orcidID,
          pastprojects,
          position,
          emailsettings
        ))
        var allProjectOptions: List[String] = projects.getAllProjects()
        var allInstitutionOptions: List[String] = institutions.getAllInstitutions()
        var emailtimes: List[String] = List("daily", "hourly", "weekly", "none")
        Ok(views.html.editProfile(newbioForm, allInstitutionOptions, allProjectOptions, emailtimes))
      }
      case None => {
        Redirect(routes.RedirectUtility.authenticationRequired())
      }
    } 
  }

  def viewProfileUUID(uuid: UUID) = SecuredAction() { request =>
    implicit val user = request.user
    val viewerUser = request.user
    var ownProfile: Option[Boolean] = None
    var muser = users.findById(uuid)
    muser match {
      case Some(existingUser) => {
        viewerUser match {
          case Some(loggedInUser) => {
            if (loggedInUser.id == existingUser.id)
              ownProfile = Option(true)
            else
              ownProfile = None
          }
          case None => {
            ownProfile = None
          }
        }
        Ok(views.html.profile(existingUser, ownProfile))
      }
      case None => {
        Logger.error("no user model exists for " + uuid.stringify)
        InternalServerError
      }
    }
  }

  def viewProfile(email: Option[String]) = SecuredAction() { request =>
    implicit val user = request.user
    var ownProfile: Option[Boolean] = None
    email match {
      case Some(addr) => {
        val modeluser = users.findByEmail(addr.toString())
        modeluser match {
          case Some(muser) => {
            user match{
              case Some(loggedIn) => {
                loggedIn.email match{
                  case Some(loggedEmail) => {
                    if (loggedEmail.toString == addr.toString())
                      ownProfile = Option(true)
                    else
                      ownProfile = None
                  }
                }
              }
              case None => { ownProfile = None }
            }
            Ok(views.html.profile(muser, ownProfile))
          }
          case None => {
            Logger.error("no user model exists for " + addr.toString())
            InternalServerError
          }
        }
      }
      case None => {
        user match {
          case Some(loggedInUser) => {
            Redirect(routes.Profile.viewProfile(loggedInUser.email))
          }
          case None => {
            Redirect(routes.RedirectUtility.authenticationRequired())
          }
        }
      }
    }
  }

  def submitChanges = SecuredAction() {  implicit request =>
    implicit val user  = request.user
    bioForm.bindFromRequest.fold(
      errors => BadRequest(views.html.editProfile(errors, List.empty, List.empty, List.empty)),
      form => {
        user match {
          case Some(x) => {
            print(x.email.toString())
            implicit val email = x.email
            email match {
              case Some(addr) => {
                implicit val modeluser = users.findByEmail(addr.toString())

                    users.updateUserField(addr.toString(), "avatarUrl", form.avatarUrl)
                    users.updateUserField(addr.toString(), "biography", form.biography)
                    users.updateUserField(addr.toString(), "currentprojects", form.currentprojects)
                    users.updateUserField(addr.toString(), "institution", form.institution)
                    users.updateUserField(addr.toString(), "orcidID", form.orcidID)
                    users.updateUserField(addr.toString(), "pastprojects", form.pastprojects)
                    users.updateUserField(addr.toString(), "position", form.position)
                    
                    events.addUserEvent(modeluser, "edit_profile")

                    var emailsetting = form.emailsettings
                    modeluser match {
                      case Some(x) => {
                        emailsetting match {
                          case Some (setting) => {
                            scheduler.updateEmailJob(x.id, "Digest[" + x.id + "]", setting)
                            }
                          }
                        }
                      }
                    
                    
                    
                  
                 
                  Redirect(routes.Profile.viewProfile(email))
                  
                
              }
            }
          }
          case None => {
            Redirect(routes.RedirectUtility.authenticationRequired())
          }
        }
      }
    )
  }
  
}
