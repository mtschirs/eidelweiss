/**
 * Copyright (c) 2014-2026 Governikus GmbH & Co. KG, Germany
 */

#include "StateDidAuthenticateEac2.h"

#include "CardConnection.h"

#if defined(EIDELWEISS_EAC2_LAB) && defined(Q_OS_ANDROID)
#include <QJniObject>
#include <QNativeInterface>
#endif

using namespace governikus;

StateDidAuthenticateEac2::StateDidAuthenticateEac2(const QSharedPointer<WorkflowContext>& pContext)
	: AbstractState(pContext)
	, GenericContextContainer(pContext)
{
	setAbortOnCardRemoved();
}


void StateDidAuthenticateEac2::run()
{
	const auto& context = getContext();
	Q_ASSERT(!context->getDidAuthenticateEac2().isNull());
	Q_ASSERT(!context->getCardConnection().isNull());
	Q_ASSERT(context->getPaceOutputData() != nullptr);
	auto cardConnection = context->getCardConnection();
	const auto ephemeralPublicKeyAsHex = context->getDidAuthenticateEac2()->getEphemeralPublicKey().toLatin1();
	QByteArray authenticatedAuxiliaryDataAsBinary = context->getDidAuthenticateEac1()->getAuthenticatedAuxiliaryDataAsBinary();

	QByteArray signatureAsHex;
	if (!context->getDidAuthenticateEac2()->getSignature().isEmpty())
	{
		signatureAsHex = context->getDidAuthenticateEac2()->getSignature().toLatin1();
	}
	else if (context->getDidAuthenticateEacAdditional())
	{
		signatureAsHex = context->getDidAuthenticateEacAdditional()->getSignature().toLatin1();
	}

	auto cvcChain = context->getChainForCertificationAuthority(*context->getPaceOutputData());
	if (!cvcChain.isValid())
	{
		updateStatus(GlobalStatus::Code::Workflow_No_Permission_Error);
		Q_EMIT fireAbort(FailureCode::Reason::Did_Authenticate_Eac2_Invalid_Cvc_Chain);
		return;
	}

	// One-shot fault arming is consumed from the app-private filesystem. The
	// AusweisApp AAR service runs in :ausweisapp2_service, not the UI process.
	// Never use this gate outside an explicit, instrumented Android lab build.
	bool injectPostTaError = false;
#if defined(EIDELWEISS_EAC2_LAB) && defined(Q_OS_ANDROID)
	const auto& tokenUrl = context->getTcTokenUrl();
	if (tokenUrl.scheme() == QStringLiteral("https") &&
			tokenUrl.host() == QStringLiteral("test.governikus-eid.de") &&
			tokenUrl.path() == QStringLiteral("/AusweisAuskunft/WebServiceRequesterServlet"))
	{
		const QJniObject appContext = QNativeInterface::QAndroidApplication::context();
		if (appContext.isValid())
		{
			const QJniObject name = QJniObject::fromString(QStringLiteral("eidelweiss_eac2_post_ta_once"));
			const QJniObject flag = appContext.callObjectMethod(
					"getFileStreamPath", "(Ljava/lang/String;)Ljava/io/File;", name.object<jstring>());
			if (flag.isValid() && flag.callMethod<jboolean>("exists") == JNI_TRUE)
			{
				// Consume the flag atomically from the perspective of this one workflow.
				injectPostTaError = flag.callMethod<jboolean>("delete") == JNI_TRUE;
				qInfo() << "EIDELWEISS_EAC2_LAB gate=consumed inject=" << injectPostTaError;
			}
		}
	}
#endif
	*this << cardConnection->callDidAuthenticateEAC2Command(this,
			&StateDidAuthenticateEac2::onCardCommandDone, cvcChain, ephemeralPublicKeyAsHex,
			signatureAsHex, authenticatedAuxiliaryDataAsBinary, context->getPin().toLatin1(), injectPostTaError);
}


void StateDidAuthenticateEac2::onCardCommandDone(QSharedPointer<BaseCardCommand> pCommand)
{
	const CardReturnCode returnCode = pCommand->getReturnCode();
#if defined(EIDELWEISS_EAC2_LAB)
	qInfo() << "EIDELWEISS_EAC2_LAB phase=eac2_command result="
			<< Enum<CardReturnCode>::getName(returnCode);
#endif
	if (returnCode != CardReturnCode::OK)
	{
		GlobalStatus::Code newStatus;
		switch (returnCode)
		{
			case CardReturnCode::COMMAND_FAILED:
				newStatus = GlobalStatus::Code::Workflow_Card_Removed;
				break;

			case CardReturnCode::WRONG_LENGTH:
				newStatus = GlobalStatus::Code::Workflow_Wrong_Length_Error;
				break;

			default:
				newStatus = GlobalStatus::Code::Workflow_No_Permission_Error;
		}
		updateStatus(newStatus);
		Q_EMIT fireAbort({FailureCode::Reason::Did_Authenticate_Eac2_Card_Command_Failed,
						  {FailureCode::Info::Card_Return_Code, Enum<CardReturnCode>::getName(returnCode)}
				});
		return;
	}

	auto eac2Command = pCommand.staticCast<DidAuthenticateEAC2Command>();
	QSharedPointer<DIDAuthenticateResponseEAC2> response = getContext()->getDidAuthenticateResponseEac2();
	response->setAuthenticationToken(eac2Command->getAuthTokenAsHex());
	response->setEfCardSecurity(eac2Command->getEfCardSecurityAsHex());
	response->setNonce(eac2Command->getNonceAsHex());

	Q_EMIT fireContinue();
}


void StateDidAuthenticateEac2::onEntry(QEvent* pEvent)
{
	//: ALL_PLATFORMS Status message after the PIN was entered, Card Authentication.
	getContext()->setProgress(40, tr("eID is being verified"));
	AbstractState::onEntry(pEvent);
}
