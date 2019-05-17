package services

import org.specs2.mutable.Specification
import play.api.libs.json._

class UserInfoProviderSpec extends Specification {

  "UserInfoProvider" should {

    "correctly parse Google People API responses" in {
      "when user has a profile picture" in {
        val json = Json.parse(
          """
            |{
            |  "resourceName": "people/123456789",
            |  "etag": "%ABCDEFG==",
            |  "names": [
            |    {
            |      "metadata": {
            |        "primary": true,
            |        "source": {
            |          "type": "PROFILE",
            |          "id": "123456789"
            |        }
            |      },
            |      "displayName": "John Smith",
            |      "familyName": "Smith",
            |      "givenName": "John",
            |      "displayNameLastFirst": "Smith, John"
            |    }
            |  ],
            |  "photos": [
            |    {
            |      "metadata": {
            |        "primary": true,
            |        "source": {
            |          "type": "PROFILE",
            |          "id": "123456789"
            |        }
            |      },
            |      "url": "https://lh4.googleusercontent.com/-Oa_abcdefg/AAAAAAAAAAI/AAAAAAAAAKE/abcdefg/s100/photo.jpg"
            |    }
            |  ]
            |}
          """.stripMargin)

        val (name, avatar) = json.as(UserInfoProvider.googlePeopleReads)
        name must beSome("John Smith")
        avatar must beSome("https://lh4.googleusercontent.com/-Oa_abcdefg/AAAAAAAAAAI/AAAAAAAAAKE/abcdefg/s100/photo.jpg")
      }

      "when user doesn't have a profile picture" in {
        val json = Json.parse(
          """
            |{
            |  "resourceName": "people/123456789",
            |  "etag": "%ABCDEFG==",
            |  "names": [
            |    {
            |      "metadata": {
            |        "primary": true,
            |        "source": {
            |          "type": "PROFILE",
            |          "id": "123456789"
            |        }
            |      },
            |      "displayName": "John Smith",
            |      "familyName": "Smith",
            |      "givenName": "John",
            |      "displayNameLastFirst": "Smith, John"
            |    }
            |  ]
            |}
          """.stripMargin)
        val (name, avatar) = json.as(UserInfoProvider.googlePeopleReads)
        name must beSome("John Smith")
        avatar must beNone
      }
      "when user doesn't have a name" in {
        val json = Json.parse(
          """
            |{
            |  "resourceName": "people/123456789",
            |  "etag": "%ABCDEFG==",
            |  "photos": [
            |    {
            |      "metadata": {
            |        "primary": true,
            |        "source": {
            |          "type": "PROFILE",
            |          "id": "123456789"
            |        }
            |      },
            |      "url": "https://lh4.googleusercontent.com/-Oa_abcdefg/AAAAAAAAAAI/AAAAAAAAAKE/abcdefg/s100/photo.jpg"
            |    }
            |  ]
            |}
          """.stripMargin)
        val (name, avatar) = json.as(UserInfoProvider.googlePeopleReads)
        name must beNone
        avatar must beSome("https://lh4.googleusercontent.com/-Oa_abcdefg/AAAAAAAAAAI/AAAAAAAAAKE/abcdefg/s100/photo.jpg")
      }
    }
  }

}
