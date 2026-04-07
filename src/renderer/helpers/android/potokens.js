
import android from 'android'
import { awaitAsyncResult } from './jsinterface'
import i18n from '../../i18n'

export async function generatePOToken(videoId, sessionContext) {
  const id = android.generatePOToken(videoId, sessionContext)
  return await awaitAsyncResult(id)
}

export async function runDecipherScript(id, code, timeout = 10000) {
  return new Promise(async (resolve, reject) => {
    setTimeout(() => {
      reject(new Error(i18n.global.t('Decipher Script Timed Out')))
    }, timeout)
    try {
      resolve(JSON.parse(await awaitAsyncResult(android.runDecipherScript(id, code, timeout))))
    } catch (ex) {
      reject(ex)
    }
  })
}