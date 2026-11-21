#!/usr/bin/env node
/**
 * Dependency-free SMTP sink for local email verification.
 * Writes each received message to mail-sink.log as a JSON line.
 */
import net from 'node:net'
import fs from 'node:fs'
import path from 'node:path'

const port = Number(process.argv[2] || 1025)
const logFile = process.argv[3] || path.join(process.cwd(), 'mail-sink.log')

const server = net.createServer((socket) => {
  socket.setEncoding('utf8')
  socket.write('220 localhost Shiye SMTP sink\r\n')
  let input = ''
  let dataMode = false
  let message = ''
  let envelope = { from: '', to: [] }

  const reset = () => {
    dataMode = false
    message = ''
    envelope = { from: '', to: [] }
  }

  socket.on('data', (chunk) => {
    input += chunk
    let index
    while ((index = input.indexOf('\r\n')) >= 0) {
      const line = input.slice(0, index)
      input = input.slice(index + 2)

      if (dataMode) {
        if (line === '.') {
          dataMode = false
          fs.appendFileSync(logFile, JSON.stringify({
            receivedAt: new Date().toISOString(),
            from: envelope.from,
            to: envelope.to,
            message,
          }) + '\n')
          message = ''
          socket.write('250 OK queued\r\n')
        } else {
          message += line + '\n'
        }
        continue
      }

      const upper = line.toUpperCase()
      if (upper.startsWith('EHLO') || upper.startsWith('HELO')) {
        socket.write('250-localhost\r\n250-8BITMIME\r\n250 OK\r\n')
      } else if (upper.startsWith('MAIL FROM:')) {
        envelope.from = line.slice(line.indexOf(':') + 1).trim()
        socket.write('250 OK\r\n')
      } else if (upper.startsWith('RCPT TO:')) {
        envelope.to.push(line.slice(line.indexOf(':') + 1).trim())
        socket.write('250 OK\r\n')
      } else if (upper === 'DATA') {
        dataMode = true
        message = ''
        socket.write('354 End data with <CR><LF>.<CR><LF>\r\n')
      } else if (upper === 'RSET') {
        reset()
        socket.write('250 OK\r\n')
      } else if (upper === 'QUIT') {
        socket.write('221 Bye\r\n')
        socket.end()
      } else if (upper === 'NOOP') {
        socket.write('250 OK\r\n')
      } else {
        socket.write('250 OK\r\n')
      }
    }
  })
  socket.on('error', () => undefined)
})

server.listen(port, '127.0.0.1', () => {
  console.log(`SMTP sink listening on 127.0.0.1:${port}, log=${logFile}`)
})
