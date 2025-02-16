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

package controllers.income.estimatedPay.update

import controllers.TaiBaseController
import controllers.actions.ValidatePerson
import controllers.auth.{AuthAction, AuthedUser}
import javax.inject.{Inject, Named}
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.cacheResolver.estimatedPay.UpdatedEstimatedPayJourneyCache
import uk.gov.hmrc.tai.connectors.responses.{TaiResponse, TaiSuccessResponseWithPayload}
import uk.gov.hmrc.tai.forms.HowToUpdateForm
import uk.gov.hmrc.tai.model.{EmploymentAmount, TaxYear}
import uk.gov.hmrc.tai.model.domain.Employment
import uk.gov.hmrc.tai.model.domain.income.{IncomeSource, TaxCodeIncome}
import uk.gov.hmrc.tai.service.journeyCache.JourneyCacheService
import uk.gov.hmrc.tai.service.{EmploymentService, IncomeService, TaxAccountService}
import uk.gov.hmrc.tai.util.constants.{JourneyCacheConstants, TaiConstants}
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import uk.gov.hmrc.play.partials.FormPartialRetriever
import uk.gov.hmrc.renderer.TemplateRenderer

import scala.concurrent.Future
import scala.util.control.NonFatal

class IncomeUpdateHowToUpdateController @Inject()(
  authenticate: AuthAction,
  validatePerson: ValidatePerson,
  employmentService: EmploymentService,
  incomeService: IncomeService,
  taxAccountService: TaxAccountService,
  @Named("Update Income") implicit val journeyCacheService: JourneyCacheService,
  override implicit val partialRetriever: FormPartialRetriever,
  override implicit val templateRenderer: TemplateRenderer)
    extends TaiBaseController with JourneyCacheConstants with UpdatedEstimatedPayJourneyCache {

  private def incomeTypeIdentifier(isPension: Boolean): String =
    if (isPension) {
      TaiConstants.IncomeTypePension
    } else {
      TaiConstants.IncomeTypeEmployment
    }

  private def cacheEmploymentDetails(id: Int, employmentFuture: Future[Option[Employment]])(
    implicit hc: HeaderCarrier): Future[Map[String, String]] =
    employmentFuture flatMap {
      case Some(employment) =>
        val incomeType = incomeTypeIdentifier(employment.receivingOccupationalPension)
        journeyCache(
          cacheMap = Map(
            UpdateIncome_NameKey       -> employment.name,
            UpdateIncome_IdKey         -> id.toString,
            UpdateIncome_IncomeTypeKey -> incomeType))
      case _ => throw new RuntimeException("Not able to find employment")
    }

  def howToUpdatePage(id: Int): Action[AnyContent] = (authenticate andThen validatePerson).async { implicit request =>
    implicit val user = request.taiUser
    val nino = user.nino

    (employmentService.employment(nino, id) flatMap {
      case Some(employment: Employment) =>
        val incomeToEditFuture = incomeService.employmentAmount(nino, id)
        val taxCodeIncomeDetailsFuture = taxAccountService.taxCodeIncomes(nino, TaxYear())
        val cacheEmploymentDetailsFuture = cacheEmploymentDetails(id, employmentService.employment(nino, id))

        for {
          incomeToEdit: EmploymentAmount <- incomeToEditFuture
          taxCodeIncomeDetails           <- taxCodeIncomeDetailsFuture
          _                              <- cacheEmploymentDetailsFuture
          result                         <- processHowToUpdatePage(id, employment.name, incomeToEdit, taxCodeIncomeDetails)

        } yield {
          result
        }
      case None => throw new RuntimeException("Not able to find employment")
    }).recover {
      case NonFatal(e) => internalServerError(e.getMessage)
    }
  }

  def processHowToUpdatePage(
    id: Int,
    employmentName: String,
    incomeToEdit: EmploymentAmount,
    taxCodeIncomeDetails: TaiResponse)(implicit request: Request[AnyContent], user: AuthedUser): Future[Result] =
    (incomeToEdit.isLive, incomeToEdit.isOccupationalPension, taxCodeIncomeDetails) match {
      case (true, false, TaiSuccessResponseWithPayload(taxCodeIncomes: Seq[TaxCodeIncome])) => {

        for {
          howToUpdate <- journeyCacheService.currentValue(UpdateIncome_HowToUpdateKey)
        } yield {
          val form = HowToUpdateForm.createForm().fill(HowToUpdateForm(howToUpdate))

          if (incomeService.editableIncomes(taxCodeIncomes).size > 1) {
            Ok(views.html.incomes.howToUpdate(form, id, employmentName))
          } else {
            incomeService.singularIncomeId(taxCodeIncomes) match {

              case Some(incomeId) => Ok(views.html.incomes.howToUpdate(form, incomeId, employmentName))

              case None => throw new RuntimeException("Employment id not present")
            }
          }

        }

      }
      case (false, false, _) => Future.successful(Redirect(controllers.routes.TaxAccountSummaryController.onPageLoad()))
      case _                 => Future.successful(Redirect(controllers.routes.IncomeController.pensionIncome()))
    }

  def handleChooseHowToUpdate: Action[AnyContent] = (authenticate andThen validatePerson).async { implicit request =>
    implicit val user = request.taiUser

    HowToUpdateForm
      .createForm()
      .bindFromRequest()
      .fold(
        formWithErrors => {
          val employerFuture = IncomeSource.create(journeyCacheService)
          for {
            employer <- employerFuture
          } yield {
            BadRequest(views.html.incomes.howToUpdate(formWithErrors, employer.id, employer.name))
          }
        },
        formData => {
          journeyCacheService.cache(UpdateIncome_HowToUpdateKey, formData.howToUpdate.getOrElse("")).map { _ =>
            formData.howToUpdate match {
              case Some("incomeCalculator") => {
                Redirect(routes.IncomeUpdateWorkingHoursController.workingHoursPage())
              }
              case _ => {
                Redirect(controllers.routes.IncomeController.viewIncomeForEdit())
              }
            }
          }
        }
      )
  }
}
