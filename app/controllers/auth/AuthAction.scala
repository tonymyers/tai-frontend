/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.auth

import com.google.inject.{ImplementedBy, Inject, Singleton}
import controllers.routes
import play.api.mvc.Results.Redirect
import play.api.mvc._
import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.{Name, ~}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.tai.model.domain.{Person, PersonCorruptDataException, PersonDeceasedException}
import uk.gov.hmrc.tai.service.PersonService

import scala.concurrent.{ExecutionContext, Future}

case class AuthenticatedRequest[A](request: Request[A], taiUser: AuthActionedTaiUser) extends WrappedRequest[A](request)

case class AuthActionedTaiUser(name: String, validNino: String) {
  def getDisplayName = name

  def getNino = validNino

  def nino: Nino = Nino(validNino)
}

@Singleton
class AuthActionImpl @Inject()(personService: PersonService,
                               override val authConnector: core.AuthConnector)
                              (implicit ec: ExecutionContext) extends AuthAction
  with AuthorisedFunctions {

  def processBlock[A](person: Person,
                      name: Option[Name],
                      nino: Option[String],
                      block: AuthenticatedRequest[A] => Future[Result],
                      request: Request[A]): Future[Result] = {
    if (person.isDeceased) {
      throw new PersonDeceasedException
    } else if (person.hasCorruptData) {
      throw new PersonCorruptDataException
    }
    else {
      for {
        taiUser <- getTaiUser(name, nino)
        result <- block(AuthenticatedRequest(request, taiUser))
      } yield {
        result
      }
    }
  }

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    authorised(ConfidenceLevel.L200 and AffinityGroup.Individual)
      .retrieve(Retrievals.nino and Retrievals.name) {
        case nino ~ name => {
          for {
            person <- personService.personDetails(Nino(nino.getOrElse("")))
            result <- processBlock(person, name, nino, block, request)
          } yield {
            result
          }
        }

        case _ => {
          Future.successful(Redirect(routes.NoCYIncomeTaxErrorController.noCYIncomeTaxErrorPage()))
        }
      } recover {
      case _: UnsupportedAffinityGroup => {
        Redirect(routes.NoCYIncomeTaxErrorController.noCYIncomeTaxErrorPage())
      }
      case _: InsufficientConfidenceLevel => {
        Redirect(routes.NoCYIncomeTaxErrorController.noCYIncomeTaxErrorPage())
      }
      case _: PersonDeceasedException => {
        Redirect(routes.DeceasedController.deceased())
      }
      case _: PersonCorruptDataException => {
        Redirect(routes.ServiceController.gateKeeper())
      }
    }
  }

  def getTaiUser(name: Option[Name], nino: Option[String]): Future[AuthActionedTaiUser] = {
    val validNino = nino.getOrElse("")
    val validName = name.flatMap(_.name)
    Future.successful(AuthActionedTaiUser(validName.getOrElse(""), validNino))
  }
}

@ImplementedBy(classOf[AuthActionImpl])
trait AuthAction extends ActionBuilder[AuthenticatedRequest] with ActionFunction[Request, AuthenticatedRequest]
