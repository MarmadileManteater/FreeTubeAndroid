// This runs in the sigView webview
window.addEventListener('message', (event) => {
  const id = event.id
  const code = Android.readSync(id)
  try {
    const result = new Function(code)()
    Android.resolve(
      id,
      // eslint-disable-next-line no-new-func
      JSON.stringify(result)
    )
  } catch (ex) {
    Android.reject(
      id,
      ex.toString()
    )
  }
})
