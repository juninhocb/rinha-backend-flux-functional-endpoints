package com.example.rinhaback2.person

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

@Component
class PersonHandler(private val repository: PersonRepository,
                    private val redisTemplate: RedisTemplate<String, Person>) {

    fun create(request: ServerRequest): Mono<ServerResponse> {
        return request.bodyToMono(Person::class.java).flatMap {

            if (it.nome.length > 100
                || it.apelido.length > 32
                || !it.nascimento.matches("\\d{4}-\\d{2}-\\d{2}".toRegex())
            ) {
                return@flatMap ServerResponse.unprocessableEntity().build()
            }


            val addedNickname = redisTemplate.opsForValue().get(it.apelido)

            if (addedNickname != null) {
                return@flatMap ServerResponse.unprocessableEntity().build()
            }


            val id = repository.save(it)

            val p = Person(
                id = id,
                nome = it.nome,
                apelido = it.apelido,
                nascimento = it.nascimento,
                stack = it.stack
            )

            redisTemplate.opsForValue().set(id, p)
            redisTemplate.opsForValue().set(p.apelido, p)

            val uri = UriComponentsBuilder.fromUriString("http://localhost:9999/pessoas/$id").build().toUri()
            ServerResponse.created(uri).build()
        }

    }

    fun findById(request: ServerRequest): Mono<ServerResponse> {
        val id = request.pathVariable("id")

        val cachedPerson = redisTemplate.opsForValue().get(id)

        return if (cachedPerson != null) {
            ServerResponse.ok().bodyValue(cachedPerson)
        } else {
            //ServerResponse.ok().body(repository.findById(UUID.fromString(id)), Person::class.java)
            ServerResponse.badRequest().build()
        }
    }

    fun findByCriteria(request: ServerRequest): Mono<ServerResponse> {

        val criteria = request.queryParam("t").orElse("")

        if (criteria.isBlank()) {
            return ServerResponse.badRequest().build()
        }
        return ServerResponse.ok().body(Mono.just(repository.findByCriteria(criteria)), List::class.java)
    }

    fun count(request: ServerRequest?): Mono<ServerResponse> {
        return ServerResponse.ok().body(Mono.just(repository.count()), Long::class.java)
    }
}