// This runs in the sigView webview
window.addEventListener('message', (event) => {
  const id = event.id
  console.log(id)
  const code = Android.readSync(id)
  console.log(code)
  try {
    const result = new Function(code)()
    console.log(id, result)
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
