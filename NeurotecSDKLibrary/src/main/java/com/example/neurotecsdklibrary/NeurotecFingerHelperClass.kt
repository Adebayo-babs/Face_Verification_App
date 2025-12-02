package com.example.neurotecsdklibrary

import android.content.Context
import com.neurotec.biometrics.NBiometricStatus
import com.neurotec.biometrics.NFPosition
import com.neurotec.biometrics.NFRecord
import com.neurotec.biometrics.NFTemplate
import com.neurotec.biometrics.NFinger
import com.neurotec.biometrics.NMatchingSpeed
import com.neurotec.biometrics.NSubject
import com.neurotec.biometrics.NTemplate
import com.neurotec.biometrics.client.NBiometricClient
import com.neurotec.biometrics.standards.BDIFStandard
import com.neurotec.biometrics.standards.FMCRMinutiaFormat
import com.neurotec.biometrics.standards.FMCRecord
import com.neurotec.io.NBuffer

class NeurotecFingerHelperClass {

    fun verifyFinger(cardData: ByteArray, scannedWSQData: ByteArray, context: Context): Boolean {
        val nBiometricClient = NBiometricClient()
        nBiometricClient.matchingThreshold = 48
        nBiometricClient.fingersMatchingSpeed = NMatchingSpeed.LOW
        nBiometricClient.initialize()

        val subjectOne = createSubjectFromCard(listOf(cardData))
        val subjectTwo = createSubject(scannedWSQData) // createSubject(accessFile(context))

        val status = nBiometricClient.verify(subjectOne, subjectTwo)
        return status.value == NBiometricStatus.OK.value
    }

    private fun accessFile(context: Context): ByteArray {
        val assetMan = context.assets
        val assetDataStream = assetMan.open("scannedWSQ.wsq")
        return assetDataStream.readBytes()
    }

    private fun createSubjectFromCard(cardData: List<ByteArray>) : NSubject {

        val nSubject = NSubject()
        val nFTemplate = NFTemplate()
        val nTemplate = NTemplate()

        for (finger in cardData) {
            val fingerRecord = createFingerFromTemplate(finger)
            nFTemplate.records.add(fingerRecord)
        }

        nTemplate.fingers = nFTemplate
        nSubject.template = nTemplate

        return nSubject
    }

    private fun createFingerFromTemplate(eachCardFinger: ByteArray) : NFRecord {

        val fmcRecord = FMCRecord(BDIFStandard.ISO, FMCRecord.VERSION_ISO_20, FMCRMinutiaFormat.COMPACT_SIZE)
        fmcRecord.minutiaeBuffer = NBuffer(eachCardFinger)
        val nfRecord = fmcRecord.toNFRecord()
        nfRecord.position = NFPosition.UNKNOWN

        return nfRecord
    }


    private fun createSubject(byteArray: ByteArray): NSubject {
        val subject = NSubject()
        val finger = NFinger()
        val buffer = NBuffer(byteArray)
        finger.sampleBuffer = buffer
        subject.fingers.add(finger)
        return subject
    }
}